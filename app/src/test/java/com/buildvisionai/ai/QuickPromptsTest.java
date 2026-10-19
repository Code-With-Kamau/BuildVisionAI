package com.buildvisionai.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.KenyaRatePack;
import com.buildvisionai.models.BuildingType;
import com.buildvisionai.models.Project;
import com.buildvisionai.planner.PlanDocument;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The two quick-question chips on the assistant sheet send the text of a string resource as the
 * question, in whatever language the app is showing. The offline assistant answers by keyword, so a
 * translation edit that drops the keyword would make a chip silently fall through to the "I can only
 * do two things" reply. This reads the real string files and sends each chip through the assistant.
 */
public class QuickPromptsTest {
    private static File resDir() {
        String prop = System.getProperty("bv.res.dir");
        File[] candidates = {prop == null ? null : new File(prop), new File("src/main/res"), new File("app/src/main/res")};
        for (File f : candidates) if (f != null && new File(f, "values/strings.xml").isFile()) return f;
        throw new AssertionError("res directory not found; run from app/ or pass -Dbv.res.dir");
    }

    private static String string(String folder, String name) throws Exception {
        String xml = new String(Files.readAllBytes(new File(resDir(), folder + "/strings.xml").toPath()), "UTF-8");
        Matcher m = Pattern.compile("<string name=\"" + name + "\"[^>]*>(.*?)</string>", Pattern.DOTALL).matcher(xml);
        assertTrue(folder + ": missing " + name, m.find());
        return m.group(1).replace("\\'", "'").replace("&amp;", "&");
    }

    private static AssistantReply ask(OfflineAssistant a, String question) {
        final List<AssistantReply> got = new ArrayList<>();
        a.ask(new AssistantRequest(question, ""), new AiAssistant.Callback() {
            public void onReply(AssistantReply r) { got.add(r); }
            public void onError(String m) { throw new AssertionError(m); }
        });
        assertEquals(1, got.size());
        return got.get(0);
    }

    private static OfflineAssistant assistant() {
        PlanDocument d = com.buildvisionai.estimation.TestPlansAccess.bungalow();
        Project p = new Project();
        p.buildingType = BuildingType.BUNGALOW;
        p.budgetKes = 1_000_000;   // far below the real cost, so the budget answer has options to show
        return new OfflineAssistant(d, p, new EstimateSettings(), KenyaRatePack.nairobi());
    }

    private void chipsRoute(String folder) throws Exception {
        String save = string(folder, "assistant_quick_save");
        String check = string(folder, "assistant_quick_check");
        OfflineAssistant a = assistant();

        AssistantReply budget = ask(a, save);
        assertFalse(folder + ": the save chip fell through to the generic reply: " + budget.text, budget.text.startsWith("Offline, I can"));
        assertFalse(folder + ": the save chip gave no options", budget.proposals.isEmpty());

        AssistantReply review = ask(a, check);
        assertNotNull(review.text);
        assertFalse(folder + ": the check chip fell through to the generic reply: " + review.text, review.text.startsWith("Offline, I can"));
        assertTrue(folder + ": the check chip must not be read as a budget question", review.proposals.isEmpty());
    }

    @Test public void englishChipsReachTheirAnswers() throws Exception { chipsRoute("values"); }

    @Test public void swahiliChipsReachTheirAnswers() throws Exception { chipsRoute("values-sw"); }

    @Test public void keywordsAreLowerCaseBecauseTheQuestionIsLowerCasedBeforeMatching() {
        for (String w : OfflineAssistant.BUDGET_WORDS) assertEquals(w, w.toLowerCase(java.util.Locale.US));
        for (String w : OfflineAssistant.CHECK_WORDS) assertEquals(w, w.toLowerCase(java.util.Locale.US));
        // no word may belong to both lists, or the budget branch (tested first) would swallow review questions
        for (String b : OfflineAssistant.BUDGET_WORDS) for (String c : OfflineAssistant.CHECK_WORDS) {
            assertFalse(b + " / " + c + " overlap", b.contains(c) || c.contains(b));
        }
    }
}
