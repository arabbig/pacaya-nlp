package edu.jhu.nlp.depparse;

import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.data.simple.AnnoSentenceCollection;
import edu.jhu.nlp.data.simple.AnnoSentenceReaderSpeedTest;
import edu.jhu.pacaya.autodiff.erma.ErmaBp;
import edu.jhu.pacaya.autodiff.erma.ErmaBp.ErmaBpPrm;
import edu.jhu.pacaya.gm.data.UFgExample;
import edu.jhu.pacaya.gm.inf.BeliefPropagation.BpScheduleType;
import edu.jhu.pacaya.gm.inf.BeliefPropagation.BpUpdateOrder;
import edu.jhu.pacaya.gm.model.FactorGraph;
import edu.jhu.pacaya.gm.model.Var;
import edu.jhu.pacaya.util.semiring.LogSemiring;
import edu.jhu.prim.util.Timer;

public class DepParseInferenceSpeedTest {
    
    /**
     * Speed test results.
     * 
     * If we comment out the IllegalStateException in ExpFamFactor to avoid updating from a model:
     *   Tokens / sec: 1681.4190792596107
     */
    //@Test
    public void testSpeed() {
        AnnoSentenceCollection sents = AnnoSentenceReaderSpeedTest.readPtbYmConllx();
        
        Timer t = new Timer();
        int s=0;
        int n=0;
        for (AnnoSentence sent : sents) {
            UFgExample ex = DepParseFactorGraphBuilderSpeedTest.get1stOrderFg(sent);
            FactorGraph fg = ex.getFgLatPred();
            
            t.start();
            runBp(fg);
            t.stop();
            
            n+=sent.size();
            if (s++%100 == 0) {
                System.out.println("Tokens / sec: " + (n / t.totSec()));
            }
        }
        System.out.println("Total secs: " + t.totSec());
        System.out.println("Tokens / sec: " + (sents.getNumTokens() / t.totSec()));
    }

    public static ErmaBp runBp(FactorGraph fg) {
        return runBp(fg, 1);
    }

    public static ErmaBp runBp(FactorGraph fg, int numIters) {
        ErmaBpPrm bpPrm = new ErmaBpPrm();
        bpPrm.maxIterations = numIters;
        bpPrm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        bpPrm.schedule = BpScheduleType.TREE_LIKE;
        bpPrm.s = LogSemiring.LOG_SEMIRING;
        ErmaBp bp = new ErmaBp(fg, bpPrm);
        bp.run();
        for (Var v : fg.getVars()) {
            bp.getMarginals(v);
        }
        return bp;
    }
    
    public static void main(String[] args) {
        (new DepParseInferenceSpeedTest()).testSpeed();
    }
    
}
