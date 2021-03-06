package edu.jhu.nlp.eval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.nlp.Evaluator;
import edu.jhu.nlp.data.DepGraph;
import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.data.simple.AnnoSentenceCollection;
import edu.jhu.pacaya.util.Prm;
import edu.jhu.pacaya.util.report.Reporter;

/**
 * Computes the precision, recall, and micro-averaged F1 of SRL.
 * 
 * @author mgormley
 */
// TODO: Support other options: predictSense = true, predictPredicatePosition = true.
public class SrlEvaluator implements Evaluator {

    public static class SrlEvaluatorPrm extends Prm {
        private static final long serialVersionUID = 1L;
        /** Whether to do labeled or unlabeled evaluation. */
        public boolean labeled = true;
        /** Whether to evaluate predicate sense. */
        public boolean evalPredSense = true;
        /** Whether to evaluate predicate position. */
        public boolean evalPredPosition = false;
        /** Whether to evaluate arguments (i.e. semantic roles). */
        public boolean evalRoles = true;
        public SrlEvaluatorPrm() { }
        public SrlEvaluatorPrm(boolean labeled, boolean evalSense, boolean evalPredicatePosition, boolean evalRoles) {
            this.labeled = labeled;
            this.evalPredSense = evalSense;
            this.evalPredPosition = evalPredicatePosition;
            this.evalRoles = evalRoles;
        }
    }
    
    private static final Logger log = LoggerFactory.getLogger(SrlEvaluator.class);
    private static final Reporter rep = Reporter.getReporter(SrlEvaluator.class);
    private static final String NO_LABEL = "__NO_LABEL__";
    private static final String SOME_LABEL = "__SOME_LABEL__";

    private final SrlEvaluatorPrm prm;

    private double precision;
    private double recall;
    private double f1;
    // Precision = # correctly predicted positive / # predicted positive
    // Recall = # correctly predicted positive / # true positive
    private int numCorrectPositive;
    private int numCorrectNegative;
    private int numPredictPositive;
    private int numTruePositive;
    private int numInstances;
    private int numMissing;
    
    public SrlEvaluator(SrlEvaluatorPrm prm) {
        this.prm = prm;
    }

    protected void reset() {
        precision = 0;
        recall = 0;
        f1 = 0;
        numCorrectPositive = 0;
        numCorrectNegative = 0;
        numPredictPositive = 0;
        numTruePositive = 0;
        numInstances = 0;
        numMissing = 0;
    }
    
    /** Computes the precision, recall, and micro-averaged F1 of SRL. */
    public double evaluate(AnnoSentenceCollection predSents, AnnoSentenceCollection goldSents, String dataName) {
        reset();
        
        assert predSents.size() == goldSents.size();
        
        // For each sentence.
        for (int s = 0; s < goldSents.size(); s++) {
            AnnoSentence goldSent = goldSents.get(s);
            AnnoSentence predSent = predSents.get(s);            
            accum(goldSent, predSent);
        }
        String detail = prm.labeled ? "Labeled" : "Unlabeled";
        detail += prm.evalPredSense ? "Sense" : "";
        detail += prm.evalPredPosition ? "Position" : "";
        detail += prm.evalRoles ? "Roles" : "";                
        log.debug(String.format("SRL %s # correct positives on %s: %d", detail, dataName, numCorrectPositive));
        log.debug(String.format("SRL %s # predicted positives on %s: %d", detail, dataName, numPredictPositive));
        log.debug(String.format("SRL %s # true positives on %s: %d", detail, dataName, numTruePositive));

        log.info(String.format("SRL Num sents not annotated on %s: %d", dataName, numMissing));
        log.info(String.format("SRL Num instances on %s: %d", dataName, numInstances));
        log.info(String.format("SRL %s accuracy on %s: %.4f", detail, dataName, (double)(numCorrectPositive + numCorrectNegative)/numInstances));
        log.info(String.format("SRL %s Precision on %s: %.4f", detail, dataName, precision));
        log.info(String.format("SRL %s Recall on %s: %.4f", detail, dataName, recall));
        log.info(String.format("SRL %s F1 on %s: %.4f", detail, dataName, f1));
        
        rep.report(dataName+detail+"SrlPrecision", precision);
        rep.report(dataName+detail+"SrlRecall", recall);
        rep.report(dataName+detail+"SrlF1", f1);
        
        return -f1;
    }

    /** Update the sufficient statistics with another sentence. */
    public void accum(AnnoSentence goldSent, AnnoSentence predSent) {
        DepGraph gold = (goldSent.getSrlGraph() == null) ? null : goldSent.getSrlGraph().toDepGraph();
        DepGraph pred = (predSent.getSrlGraph() == null) ? null : predSent.getSrlGraph().toDepGraph();
        
        if (gold == null) { return; }
        if (pred == null) { numMissing++; }
        
        // For each gold edge.
        int n = goldSent.size();
        for (int p=-1; p < n; p++) {          
            if (!prm.evalPredSense && !prm.evalPredPosition && p == -1) {
                // Exclude arcs from the virtual root to predicates.
                continue;
            }
            if (!prm.evalRoles && p != -1) {
                // Only consider arcs from the virtual root.
                continue;
            }
            for (int c=0; c < n; c++) {                      
                if (!prm.evalPredPosition && !hasPredicateForEdge(gold, p, c)) {
                    // Only consider predicates which appear in the gold annotations.
                    continue;
                }
                String goldLabel = getLabel(gold, p, c);
                String predLabel = getLabel(pred, p, c);
                
                if (goldLabel.equals(predLabel)) {
                    if (!goldLabel.equals(NO_LABEL)) {
                        numCorrectPositive++;
                    } else {
                        numCorrectNegative++;
                    }
                }
                if (!NO_LABEL.equals(goldLabel)) {
                    numTruePositive++;
                }
                if (!NO_LABEL.equals(predLabel)) {
                    numPredictPositive++;
                }
                numInstances++;
                log.trace(String.format("p=%d c=%d eq=%s goldLabel=%s predLabel=%s", 
                        p, c, goldLabel.equals(predLabel) ? "T" : "F", goldLabel, predLabel)); 
            }
        }

        precision = (numPredictPositive == 0) ? 0.0 : (double) numCorrectPositive / numPredictPositive;
        recall = (numTruePositive == 0) ? 0.0 :  (double) numCorrectPositive / numTruePositive;
        f1 = (precision == 0.0 && recall == 0.0) ? 0.0 : (double) (2 * precision * recall) / (precision + recall);        
    }

    private boolean hasPredicateForEdge(DepGraph gold, int p, int c) {
        if (p == -1 && gold.get(p, c) == null) {
            // The parent for this edge is a predicate not found in the gold data.
            return false;
        } else if (p != -1 && gold.get(-1, p) == null) {
            // The child for this edge is a predicate not found in the gold data.
            return false;
        } else {
            // This edge has a predicate in the gold data.
            return true;
        }
    }

    private String getLabel(DepGraph dg, int p, int c) {
        if (dg == null) {
            return null;
        }
        String label = dg.get(p, c);
        if (label == null) {
            return NO_LABEL;
        } else if (!prm.labeled || (p == -1 && !prm.evalPredSense)){
            return SOME_LABEL;
        } else {
            return label;
        }
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public double getF1() {
        return f1;
    }

    public int getNumCorrectPositive() {
        return numCorrectPositive;
    }

    public int getNumCorrectNegative() {
        return numCorrectNegative;
    }

    public int getNumPredictPositive() {
        return numPredictPositive;
    }

    public int getNumTruePositive() {
        return numTruePositive;
    }

    public int getNumInstances() {
        return numInstances;
    }

    public int getNumMissing() {
        return numMissing;
    }
    
}
