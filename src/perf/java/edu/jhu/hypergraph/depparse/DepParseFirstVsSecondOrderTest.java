package edu.jhu.hypergraph.depparse;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import edu.jhu.nlp.CorpusStatistics;
import edu.jhu.nlp.CorpusStatistics.CorpusStatisticsPrm;
import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.data.simple.AnnoSentenceCollection;
import edu.jhu.nlp.data.simple.AnnoSentenceReaderSpeedTest;
import edu.jhu.nlp.depparse.BitshiftDepParseFeatureExtractor;
import edu.jhu.nlp.depparse.BitshiftDepParseFeatureExtractor.BitshiftDepParseFeatureExtractorPrm;
import edu.jhu.nlp.depparse.DepParseDecoder;
import edu.jhu.nlp.depparse.DepParseFactorGraphBuilder;
import edu.jhu.nlp.depparse.DepParseFactorGraphBuilder.DepParseFactorGraphBuilderPrm;
import edu.jhu.nlp.depparse.DepParseFactorGraphBuilderSpeedTest;
import edu.jhu.nlp.depparse.DepParseFeatureExtractor;
import edu.jhu.nlp.depparse.DepParseFeatureExtractor.DepParseFeatureExtractorPrm;
import edu.jhu.nlp.depparse.DepParseInferenceSpeedTest;
import edu.jhu.nlp.depparse.O2AllGraFgInferencer;
import edu.jhu.nlp.features.TemplateSets;
import edu.jhu.pacaya.autodiff.Tensor;
import edu.jhu.pacaya.autodiff.erma.ErmaBp;
import edu.jhu.pacaya.gm.data.UFgExample;
import edu.jhu.pacaya.gm.data.UnlabeledFgExample;
import edu.jhu.pacaya.gm.feat.FactorTemplateList;
import edu.jhu.pacaya.gm.feat.FeatureExtractor;
import edu.jhu.pacaya.gm.feat.ObsFeatureConjoiner;
import edu.jhu.pacaya.gm.feat.ObsFeatureConjoiner.ObsFeatureConjoinerPrm;
import edu.jhu.pacaya.gm.inf.FgInferencer;
import edu.jhu.pacaya.gm.model.FactorGraph;
import edu.jhu.pacaya.gm.model.FgModel;
import edu.jhu.pacaya.gm.model.Var;
import edu.jhu.pacaya.gm.model.VarConfig;
import edu.jhu.pacaya.gm.model.VarTensor;
import edu.jhu.pacaya.gm.model.globalfac.LinkVar;
import edu.jhu.pacaya.parse.dep.EdgeScores;
import edu.jhu.pacaya.util.semiring.LogSignAlgebra;
import edu.jhu.prim.util.Timer;
import edu.jhu.prim.util.random.Prng;

/**
 * Tests comparing inference in first and second order dep parsing models.
 * @author mgormley
 */
public class DepParseFirstVsSecondOrderTest {
    
    /**
     * Commenting out lines 80 and 83 in {@link FastDepParseFeatureExtractor} allows this test to (correctly) pass.
     *   // FastDepParseFe.add2ndOrderSiblingFeats(isent, f2.i, f2.j, f2.k, feats);
     *   // FastDepParseFe.add2ndOrderGrandparentFeats(isent, f2.k, f2.i, f2.j, feats);
     *
     * This compares the behavior a first-order model and a second-order model where the second order model has 
     * some extra "dummy" factors that are only multiplying in the value 1.
     * 
     * Errors: marginals=6 parents=0
     */
    @Test
    public void testEqualMarginalsAndParentsFirstVsSecondOrder() {
        AnnoSentenceCollection sents = AnnoSentenceReaderSpeedTest.readPtbYmConllx();

        int numParams = 1000000;
        FgModel model = new FgModel(numParams);
        model.setRandomStandardNormal();
        
        CorpusStatistics cs = new CorpusStatistics(new CorpusStatisticsPrm());
        cs.init(sents);
        ObsFeatureConjoiner ofc = new ObsFeatureConjoiner(new ObsFeatureConjoinerPrm(), new FactorTemplateList());
        boolean onlyFast = true;

        int numSentsWithDiffMargs = 0;
        int numSentsWithDiffParents = 0;
        
        int s=0;
        int n=0;

        Timer t = new Timer();
        t.start();
        for (AnnoSentence sent : sents) {
            if (sent.size() > 10) { continue; }            
            FactorGraph fg1, fg2;
            ErmaBp bp1, bp2;
            int[] parents1, parents2;            
            {
                // First order
                UFgExample ex = DepParseFactorGraphBuilderSpeedTest.get1stOrderFg(sent, cs, ofc, numParams, onlyFast);
                fg1 = ex.getFgLatPred();
                fg1.updateFromModel(model);
                bp1 = DepParseInferenceSpeedTest.runBp(fg1);
                DepParseDecoder decode = new DepParseDecoder();
                parents1 = decode.decode(bp1, ex, sent);
            }
            {
                // Second order
                UFgExample ex = get2ndOrderFg(sent, cs, ofc, numParams, onlyFast);
                fg2 = ex.getFgLatPred();
                fg2.updateFromModel(model);
                bp2 = DepParseInferenceSpeedTest.runBp(fg2);
                DepParseDecoder decode = new DepParseDecoder();
                parents2 = decode.decode(bp2, ex, sent);
            }
            
            try {
                assertEqualMarginals(fg1, bp1, fg2, bp2, 1e-5);
            } catch (AssertionError e) {
                System.out.println(e.getMessage());
                numSentsWithDiffMargs++;
            }
            try {
                assertArrayEquals(parents1, parents2);
            } catch (AssertionError e) {
                numSentsWithDiffParents++;
            }
            
            n+=sent.size();
            if (s++%1 == 0) {
                t.stop();
                System.out.println(String.format("s=%d n=%d tot=%7.2f", s, n, n/t.totSec()));
                t.start();
            }
            if (s > 102) {
                break;
            }
        }
        t.stop();
        
        System.out.printf("Errors: marginals=%d parents=%d\n", numSentsWithDiffMargs, numSentsWithDiffParents);
        System.out.println("Total secs: " + t.totSec());
        System.out.println("Tokens / sec: " + (sents.getNumTokens() / t.totSec()));
    }
    
    /**
     * Compares marginals and MBR decode for different numbers of BP iterations.
     * 
     * Prng.seed(12345);
     * 2 iters vs. 10 iters: Errors: marginals=93 parents=36
     * 5 iters vs. 10 iters: Errors: marginals=14 parents=2
     * 6 iters vs. 10 iters: Errors: marginals=8 parents=2
     * 9 iters vs. 10 iters: Errors: marginals=1 parents=0
     * 
     * Prng.seed(123456789101112l);
     * 2 iters vs. 10 iters: Errors: marginals=94 parents=51
     * 5 iters vs. 10 iters: Errors: marginals=61 parents=23
     * 6 iters vs. 10 iters: Errors: marginals=42 parents=11
     * 9 iters vs. 10 iters: Errors: marginals=35 parents=16
     */
    @Test
    public void testEqualMarginalsAndParentsNumBpIters() {
        AnnoSentenceCollection sents = AnnoSentenceReaderSpeedTest.readPtbYmConllx();

        int numParams = 1000000;
        FgModel model = new FgModel(numParams);
        Prng.seed(123456789101112l);
        model.setRandomStandardNormal();
        
        CorpusStatistics cs = new CorpusStatistics(new CorpusStatisticsPrm());
        cs.init(sents);
        ObsFeatureConjoiner ofc = new ObsFeatureConjoiner(new ObsFeatureConjoinerPrm(), new FactorTemplateList());
        boolean onlyFast = true;
        
        int s=0;
        int n=0;

        int numSentsWithDiffMargs = 0;
        int numSentsWithDiffParents = 0;
        
        Timer t = new Timer();
        t.start();
        for (AnnoSentence sent : sents) {
            if (sent.size() > 10) { continue; }
            FactorGraph fg1, fg2;
            ErmaBp bp1, bp2;
            int[] parents1, parents2;            
            {
                // Second order 5 iters
                UFgExample ex = get2ndOrderFg(sent, cs, ofc, numParams, onlyFast);
                fg1 = ex.getFgLatPred();
                fg1.updateFromModel(model);
                bp1 = DepParseInferenceSpeedTest.runBp(fg1, 5);
                DepParseDecoder decode = new DepParseDecoder();
                parents1 = decode.decode(bp1, ex, sent);
            }
            {
                // Second order 10 iters
                UFgExample ex = get2ndOrderFg(sent, cs, ofc, numParams, onlyFast);
                fg2 = ex.getFgLatPred();
                fg2.updateFromModel(model);
                bp2 = DepParseInferenceSpeedTest.runBp(fg2, 10);
                DepParseDecoder decode = new DepParseDecoder();
                parents2 = decode.decode(bp2, ex, sent);
            }
            
            try {
                assertEqualMarginals(fg1, bp1, fg2, bp2, 1);
            } catch (AssertionError e) {
                numSentsWithDiffMargs++;
            }
            try {
                assertArrayEquals(parents1, parents2);
            } catch (AssertionError e) {
                numSentsWithDiffParents++;
            }
            
            n+=sent.size();
            if (s++%1 == 0) {
                t.stop();
                System.out.println(String.format("s=%d n=%d tot=%7.2f", s, n, n/t.totSec()));
                t.start();
            }
            if (s > 102) {
                break;
            }
        }
        t.stop();
        
        System.out.printf("Errors: marginals=%d parents=%d\n", numSentsWithDiffMargs, numSentsWithDiffParents);
        System.out.println("Total secs: " + t.totSec());
        System.out.println("Tokens / sec: " + (sents.getNumTokens() / t.totSec()));
    }

    public static UFgExample get2ndOrderFg(AnnoSentence sent, CorpusStatistics cs, ObsFeatureConjoiner ofc, int numParams, boolean onlyFast) {
        FactorGraph fg = new FactorGraph();
        DepParseFeatureExtractorPrm fePrm = new DepParseFeatureExtractorPrm();
        fePrm.featureHashMod = numParams;
        fePrm.firstOrderTpls = TemplateSets.getFromResource(TemplateSets.mcdonaldDepFeatsResource);
        BitshiftDepParseFeatureExtractorPrm bsFePrm = new BitshiftDepParseFeatureExtractorPrm();
        bsFePrm.featureHashMod = numParams;
        FeatureExtractor fe = onlyFast?
                new BitshiftDepParseFeatureExtractor(bsFePrm, sent, cs, ofc) :
                new DepParseFeatureExtractor(fePrm, sent, cs, ofc.getFeAlphabet());
        
        DepParseFactorGraphBuilderPrm fgPrm = new DepParseFactorGraphBuilderPrm();
        fgPrm.useProjDepTreeFactor = true;        
        fgPrm.grandparentFactors = true;
        fgPrm.arbitrarySiblingFactors = true;    
        fgPrm.pruneEdges = true;
        DepParseFactorGraphBuilder builder = new DepParseFactorGraphBuilder(fgPrm);
        builder.build(sent, fe, fg);
        
        UnlabeledFgExample ex = new UnlabeledFgExample(fg, new VarConfig());
        return ex;
    }
    
    @Test
    public void testExactVsApproxInf2ndOrderGraOnly() {
        AnnoSentenceCollection sents = AnnoSentenceReaderSpeedTest.readPtbYmConllx();

        int numParams = 1000000;
        FgModel model = new FgModel(numParams);
        Prng.seed(123456789101112l);
        model.setRandomStandardNormal();
        
        CorpusStatistics cs = new CorpusStatistics(new CorpusStatisticsPrm());
        cs.init(sents);
        ObsFeatureConjoiner ofc = new ObsFeatureConjoiner(new ObsFeatureConjoinerPrm(), new FactorTemplateList());
        boolean onlyFast = true;
        
        int s=0;
        int n=0;

        double sumDiffs = 0;
        int diffCount = 0;
        int numSentsWithDiffMargs = 0;
        int numToksWithDiffParents = 0;
        
        Timer t = new Timer();
        t.start();
        for (AnnoSentence sent : sents) {
            if (sent.size() > 10) { continue; }
            FactorGraph fg1, fg2;
            EdgeScores es1, es2;
            FgInferencer bp1, bp2;
            int[] parents1, parents2;            
            {
                // Second order 10 iters
                UFgExample ex = get2ndOrderGraOnlyFg(sent, cs, ofc, numParams, onlyFast);
                fg1 = ex.getFgLatPred();
                fg1.updateFromModel(model);
                bp1 = DepParseInferenceSpeedTest.runBp(fg1, 10);
                //es1 = DepParseDecoder.getEdgeScores(bp1, fg1, sent.size()).get1();
                DepParseDecoder decode = new DepParseDecoder();
                parents1 = decode.decode(bp1, ex, sent);
            }
            {
                // Second order exact
                UFgExample ex = get2ndOrderGraOnlyFg(sent, cs, ofc, numParams, onlyFast);
                fg2 = ex.getFgLatPred();
                fg2.updateFromModel(model);
                bp2 = new O2AllGraFgInferencer(fg2, LogSignAlgebra.LOG_SIGN_ALGEBRA);
                bp2.run();
                DepParseDecoder decode = new DepParseDecoder();
                parents2 = decode.decode(bp2, ex, sent);
            }
            
            //            for (int p=-1; p<sent.size(); p++) {
            //                for (int c=0; c<sent.size(); c++) {
            //                    if (p == c) { continue; } 
            //                    sumDiffs += Math.abs(es1.getScore(p, c) - es2.getScore(p, c));
            //                    diffCount++;
            //                    assert !Double.isInfinite(sumDiffs) && !Double.isNaN(sumDiffs);
            //                }
            //            }      
            try {
                assertEqualMarginals(fg1, bp1, fg2, bp2, 1);
            } catch (AssertionError e) {
                numSentsWithDiffMargs++;
            }
            for (int c=0; c<parents1.length; c++) {
                if (parents1[c] != parents2[c]) {
                    numToksWithDiffParents++;
                }
            }
            
            n+=sent.size();
            if (s++%1 == 0) {
                t.stop();
                System.out.println(String.format("s=%d n=%d tot=%7.2f", s, n, n/t.totSec()));
                t.start();
            }
            if (s > 102) {
                break;
            }
        }
        t.stop();
        
        System.out.printf("Errors: marginals=%d parents=%d\n", numSentsWithDiffMargs, numToksWithDiffParents);
        System.out.printf("Avg diff=%f\n", sumDiffs / diffCount);
        System.out.println("Total secs: " + t.totSec());
        System.out.println("Tokens / sec: " + (sents.getNumTokens() / t.totSec()));
    }
    
    public static UFgExample get2ndOrderGraOnlyFg(AnnoSentence sent, CorpusStatistics cs, ObsFeatureConjoiner ofc, int numParams, boolean onlyFast) {
        FactorGraph fg = new FactorGraph();
        DepParseFeatureExtractorPrm fePrm = new DepParseFeatureExtractorPrm();
        fePrm.featureHashMod = numParams;
        fePrm.firstOrderTpls = TemplateSets.getFromResource(TemplateSets.mcdonaldDepFeatsResource);
        BitshiftDepParseFeatureExtractorPrm bsFePrm = new BitshiftDepParseFeatureExtractorPrm();
        bsFePrm.featureHashMod = numParams;
        FeatureExtractor fe = onlyFast?
                new BitshiftDepParseFeatureExtractor(bsFePrm, sent, cs, ofc) :
                new DepParseFeatureExtractor(fePrm, sent, cs, ofc.getFeAlphabet());
        
        DepParseFactorGraphBuilderPrm fgPrm = new DepParseFactorGraphBuilderPrm();
        fgPrm.grandparentFactors = false;
        fgPrm.arbitrarySiblingFactors = false;    
        DepParseFactorGraphBuilder builder = new DepParseFactorGraphBuilder(fgPrm);
        builder.build(sent, fe, fg);
        
        UnlabeledFgExample ex = new UnlabeledFgExample(fg, new VarConfig());
        return ex;
    }
    
    private void assertEqualMarginals(FactorGraph fg1, FgInferencer bp1, FactorGraph fg2, FgInferencer bp2, double tolerance) {
        for (Var var1 : fg1.getVars()) {
            LinkVar lv1 = (LinkVar) var1;
            LinkVar lv2 = null;
            for (Var var2 : fg2.getVars()) {
                lv2 = (LinkVar) var2;
                if (lv1.getChild() == lv2.getChild() && lv1.getParent() == lv2.getParent()) {
                    break;
                } else {
                    lv2 = null;
                }
            }
            {
                VarTensor m1 = bp1.getMarginals(lv1);
                VarTensor m2 = bp2.getMarginals(lv2);
                // Ignore vars when testing equality
                if (!Tensor.equals(m1, m2, tolerance)) {
                    assertEquals(m1, m2);
                }
            }
            {
                VarTensor m1 = bp1.getLogMarginals(lv1);
                VarTensor m2 = bp2.getLogMarginals(lv2);
                if (!Tensor.equals(m1, m2, tolerance)) {
                    assertEquals(m1, m2);
                }
            }
        }
        //assertEquals(bp1.getPartition(), bp2.getPartition(), tolerance);
        assertEquals(bp1.getLogPartition(), bp2.getLogPartition(), tolerance);
    }
    
    public static void main(String[] args) {
        (new DepParseFirstVsSecondOrderTest()).testEqualMarginalsAndParentsNumBpIters();
    }
    
}
