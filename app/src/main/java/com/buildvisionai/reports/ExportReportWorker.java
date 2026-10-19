package com.buildvisionai.reports;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.buildvisionai.AppContainer;
import com.buildvisionai.BuildVisionApp;
import com.buildvisionai.domain.PlanChecker;
import com.buildvisionai.estimation.EstimateRunner;
import com.buildvisionai.estimation.RatePack;
import com.buildvisionai.models.Approval;
import com.buildvisionai.models.Project;
import com.buildvisionai.reports.data.ReportBuilder;
import com.buildvisionai.reports.data.ReportModel;
import com.buildvisionai.repository.ProjectRepository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds a report file in the background: a PDF of the whole report, or one PNG per drawn floor.
 * The work reads the version from the database itself (the drawing, the assumptions, the project's
 * own prices and the approval record) and runs the same engines as the estimate screen, so a file
 * always matches the numbers the person saw. The result is a list of file paths in the output data;
 * the screen shares them through FileProvider.
 *
 * <p>Files go to the app's cache folder and are removed after a day; an exported report is a copy,
 * never the record. The record is the version in the database.
 *
 */
public final class ExportReportWorker extends Worker {
    public static final String FORMAT_PDF = "pdf";
    public static final String FORMAT_PNG = "png";

    public static final String KEY_VERSION_ID = "versionId";
    public static final String KEY_FORMAT = "format";
    /** Output: the paths of the files written (String[]). */
    public static final String KEY_FILES = "files";
    /** Output of a failed run: a sentence for the person. */
    public static final String KEY_ERROR = "error";

    private static final String DIR = "reports";            // must match res/xml/file_paths.xml
    private static final long KEEP_MS = 24L * 60 * 60 * 1000;

    public ExportReportWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Queues an export and returns the id to watch with WorkManager. Call from a background thread. */
    public static UUID enqueue(Context context, String versionId, String format) {
        if (!FORMAT_PDF.equals(format) && !FORMAT_PNG.equals(format)) throw new IllegalArgumentException("Unknown export format: " + format);
        Data input = new Data.Builder().putString(KEY_VERSION_ID, versionId).putString(KEY_FORMAT, format).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ExportReportWorker.class)
                .setInputData(input)
                .addTag("export-report")
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueue(request);
        return request.getId();
    }

    @NonNull
    @Override
    public Result doWork() {
        String versionId = getInputData().getString(KEY_VERSION_ID);
        String format = getInputData().getString(KEY_FORMAT);
        if (versionId == null || format == null) return failure("The export request was incomplete.");
        try {
            AppContainer app = BuildVisionApp.of(getApplicationContext());
            ProjectRepository.LoadedVersion lv = app.projects.loadVersionSync(versionId);
            Project project = app.projects.projectSync(lv.version.projectId);
            if (project == null) return failure("The project of this version no longer exists.");

            List<String> priceWarnings = new ArrayList<>();
            RatePack pack = app.projects.ratePackSync(project.projectId, EstimateRunner.basePack(project), priceWarnings);
            EstimateRunner.Result run = EstimateRunner.run(lv.plan, lv.settings, pack);
            List<Approval> approvals = app.collaboration.approvalsSync(versionId);
            List<PlanChecker.Issue> issues = PlanChecker.check(lv.plan, project);
            ReportModel model = ReportBuilder.build(project, lv.version, lv.plan, run.quantities, run.cost, approvals, issues,
                    ReportBuilder.formatDate(System.currentTimeMillis()));
            model.warnings.addAll(priceWarnings);

            List<PlanImageExporter.Sheet> sheets = PlanImageExporter.sheets(lv.plan, model);
            if (FORMAT_PNG.equals(format) && sheets.isEmpty()) return failure("There is nothing drawn yet, so there is no plan to export.");
            if (isStopped()) return Result.failure();

            File dir = outputDir();
            String base = baseName(project, lv.version.number);
            List<File> files = new ArrayList<>();
            if (FORMAT_PDF.equals(format)) {
                File pdf = new File(dir, base + ".pdf");
                PdfReportWriter.write(pdf, model, sheets);
                files.add(pdf);
            } else {
                files.addAll(PlanImageExporter.exportPng(dir, base, sheets));
            }
            String[] paths = new String[files.size()];
            for (int i = 0; i < paths.length; i++) paths[i] = files.get(i).getAbsolutePath();
            return Result.success(new Data.Builder().putStringArray(KEY_FILES, paths).build());
        } catch (IOException | RuntimeException e) {
            return failure(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private File outputDir() throws IOException {
        File dir = new File(getApplicationContext().getCacheDir(), DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create the folder for exported reports.");
        File[] old = dir.listFiles();
        if (old != null) {
            long cutoff = System.currentTimeMillis() - KEEP_MS;
            for (File f : old) if (f.isFile() && f.lastModified() < cutoff) f.delete();
        }
        return dir;
    }

    
    static String baseName(Project project, int versionNumber) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("Africa/Nairobi"));
        String stamp = f.format(new java.util.Date());
        return "BuildVision-" + PlanImageExporter.safeName(project.projectName) + "-v" + versionNumber + "-" + stamp;
    }

    private static Result failure(String message) {
        return Result.failure(new Data.Builder().putString(KEY_ERROR, message).build());
    }
}
