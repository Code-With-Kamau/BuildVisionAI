package com.buildvisionai.ai;

import com.buildvisionai.domain.PlanChecker;
import com.buildvisionai.estimation.EstimateSettings;
import com.buildvisionai.estimation.RatePack;
import com.buildvisionai.models.Project;
import com.buildvisionai.planner.PlanDocument;

import java.util.List;
import java.util.Locale;

/**
 * Rule-based helper that works with no network (useful where data is costly). Answers budget and
 * drawing-check questions from the deterministic engines. Anything else gets a pointer to what it
 * can do; it does not pretend to understand free-form design questions.
 */
public final class OfflineAssistant implements AiAssistant {
    private final PlanDocument doc;
    private final Project project;
    private final EstimateSettings settings;
    private final RatePack pack;

    public OfflineAssistant(PlanDocument doc, Project project, EstimateSettings settings, RatePack pack) {
        this.doc = doc;
        this.project = project;
        this.settings = settings;
        this.pack = pack;
    }

    @Override public void ask(AssistantRequest request, Callback callback) {
        String q = request.question == null ? "" : request.question.toLowerCase(Locale.US);
        if (mentions(q, BUDGET_WORDS)) {
            BudgetAdvisor.Report rep = BudgetAdvisor.advise(doc, settings, pack, project.budgetKes);
            StringBuilder b = new StringBuilder(rep.summary);
            AssistantReply reply = new AssistantReply("", "offline");
            int i = 1;
            for (BudgetAdvisor.Option o : rep.options) {
                b.append(String.format(Locale.US, "%n%d. %s - saves about KES %,.0f. %s", i++, o.title, o.savingKes, o.tradeOff));
                reply.proposals.add(o.proposal);
            }
            callback.onReply(copy(b.toString(), reply));
            return;
        }
        if (mentions(q, CHECK_WORDS)) {
            List<PlanChecker.Issue> issues = PlanChecker.check(doc, project);
            StringBuilder b = new StringBuilder();
            int shown = 0;
            for (PlanChecker.Issue i : issues) {
                if (i.severity == PlanChecker.Severity.INFO) continue;
                b.append("- ").append(i.message).append('\n');
                shown++;
            }
            if (shown == 0) b.append("No problems found in the drawing. This checks the drawing only, not building regulations.");
            callback.onReply(new AssistantReply(b.toString().trim(), "offline"));
            return;
        }
        callback.onReply(new AssistantReply("Offline, I can check your budget (ask \"how can I save money?\") "
                + "and review the drawing (ask \"check my plan\"). Connect to the internet for open-ended design questions.", "offline"));
    }

    /**
     * Words that start each kind of answer. The offline assistant understands a few English and Swahili
     * keywords so the two quick-question chips work in either language; its replies are still in English.
     */
    static final String[] BUDGET_WORDS = {"budget", "cost", "cheap", "save", "afford", "bajeti", "gharama", "bei", "okoa", "nafuu"};
    static final String[] CHECK_WORDS = {"check", "issue", "problem", "review", "wrong", "angalia", "kagua", "tatizo", "shida", "kosa"};

    static boolean mentions(String lowerCaseQuestion, String[] words) {
        for (String w : words) if (lowerCaseQuestion.contains(w)) return true;
        return false;
    }

    private static AssistantReply copy(String text, AssistantReply from) {
        AssistantReply r = new AssistantReply(text, from.origin);
        r.proposals.addAll(from.proposals);
        return r;
    }
}
