package edu.jhu.pacaya.gm.extratests;

import static edu.jhu.nlp.data.simple.AnnoSentenceCollection.getSingleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.jhu.hlt.optimize.function.Function;
import edu.jhu.nlp.CorpusStatistics;
import edu.jhu.nlp.CorpusStatistics.CorpusStatisticsPrm;
import edu.jhu.nlp.data.conll.CoNLL09Sentence;
import edu.jhu.nlp.data.conll.CoNLL09Token;
import edu.jhu.nlp.data.simple.AnnoSentenceCollection;
import edu.jhu.nlp.data.simple.AnnoSentenceReader;
import edu.jhu.nlp.data.simple.AnnoSentenceReader.AnnoSentenceReaderPrm;
import edu.jhu.nlp.data.simple.AnnoSentenceReader.DatasetType;
import edu.jhu.nlp.joint.JointNlpFgExamplesBuilder;
import edu.jhu.nlp.joint.JointNlpFgExamplesBuilder.JointNlpFgExampleBuilderPrm;
import edu.jhu.nlp.srl.SrlFactorGraphBuilder.RoleStructure;
import edu.jhu.pacaya.autodiff.erma.ErmaBp.ErmaBpPrm;
import edu.jhu.pacaya.gm.data.FgExampleList;
import edu.jhu.pacaya.gm.data.FgExampleListBuilder.CacheType;
import edu.jhu.pacaya.gm.data.LFgExample;
import edu.jhu.pacaya.gm.feat.FactorTemplateList;
import edu.jhu.pacaya.gm.feat.ObsFeatureConjoiner;
import edu.jhu.pacaya.gm.feat.ObsFeatureConjoiner.ObsFeatureConjoinerPrm;
import edu.jhu.pacaya.gm.inf.BeliefPropagation;
import edu.jhu.pacaya.gm.inf.BeliefPropagation.BeliefPropagationPrm;
import edu.jhu.pacaya.gm.inf.BeliefPropagation.BpScheduleType;
import edu.jhu.pacaya.gm.inf.BeliefPropagation.BpUpdateOrder;
import edu.jhu.pacaya.gm.inf.BfsMpSchedule;
import edu.jhu.pacaya.gm.inf.FgInferencer;
import edu.jhu.pacaya.gm.inf.FgInferencerFactory;
import edu.jhu.pacaya.gm.model.Factor;
import edu.jhu.pacaya.gm.model.FactorGraph;
import edu.jhu.pacaya.gm.model.FgModel;
import edu.jhu.pacaya.gm.model.Var;
import edu.jhu.pacaya.gm.model.Var.VarType;
import edu.jhu.pacaya.gm.train.AvgBatchObjective;
import edu.jhu.pacaya.gm.train.CrfObjective;
import edu.jhu.pacaya.util.collections.Lists;
import edu.jhu.pacaya.util.semiring.Algebra;
import edu.jhu.pacaya.util.semiring.LogSemiring;
import edu.jhu.pacaya.util.semiring.LogSignAlgebra;
import edu.jhu.pacaya.util.semiring.RealAlgebra;
import edu.jhu.pacaya.util.semiring.ShiftedRealAlgebra;
import edu.jhu.pacaya.util.semiring.SplitAlgebra;
import edu.jhu.prim.tuple.Pair;
import edu.jhu.prim.util.math.FastMath;
import edu.jhu.prim.util.random.Prng;
import edu.jhu.prim.vector.IntDoubleVector;

public class CrfObjectiveTest {

    public static final String conllXExample= "/edu/jhu/nlp/data/conll/bulgarian_bultreebank_train.conll";

    @Test
    public void testSrlLogLikelihood() throws Exception {
        checkSrlLogLikelihoodCorrect(RealAlgebra.REAL_ALGEBRA);
        checkSrlLogLikelihoodCorrect(LogSemiring.LOG_SEMIRING);
    }
    
    public void checkSrlLogLikelihoodCorrect(Algebra s) {
        List<CoNLL09Token> tokens = new ArrayList<CoNLL09Token>();
        //tokens.add(new CoNLL09Token(1, "the", "_", "_", "Det", "_", getList("feat"), getList("feat") , 2, 2, "det", "_", false, "_", new ArrayList<String>()));
        //tokens.add(new CoNLL09Token(id, form, lemma, plemma, pos, ppos, feat, pfeat, head, phead, deprel, pdeprel, fillpred, pred, apreds));
//        tokens.add(new CoNLL09Token(1, "the", "_", "_", "Det", "_", getList("feat"), getList("feat") , 2, 2, "det", "_", false, "_", getList("_")));
        tokens.add(new CoNLL09Token(2, "dog", "_", "_", "N", "_", Lists.getList("feat"), Lists.getList("feat") , 2, 2, "subj", "_", false, "_", Lists.getList("arg0")));
        tokens.add(new CoNLL09Token(3, "ate", "_", "_", "V", "_", Lists.getList("feat"), Lists.getList("feat") , 0, 0, "v", "_", true, "ate.1", Lists.getList("_")));
        //tokens.add(new CoNLL09Token(4, "food", "_", "_", "N", "_", getList("feat"), getList("feat") , 2, 2, "obj", "_", false, "_", getList("arg1")));
        CoNLL09Sentence sent = new CoNLL09Sentence(tokens);
                
        System.out.println("Done reading.");
        CorpusStatisticsPrm csPrm = new CorpusStatisticsPrm();
        CorpusStatistics cs = new CorpusStatistics(csPrm);
        AnnoSentenceCollection sents = getSingleton(sent.toAnnoSentence(csPrm.useGoldSyntax));
        cs.init(sents);
        
        FactorTemplateList fts = new FactorTemplateList();
        JointNlpFgExampleBuilderPrm prm = new JointNlpFgExampleBuilderPrm();
        prm.fgPrm.srlPrm.makeUnknownPredRolesLatent = false;
        prm.fgPrm.srlPrm.roleStructure = RoleStructure.PREDS_GIVEN;
        prm.fgPrm.dpPrm.useProjDepTreeFactor = true;
        prm.fePrm.srlFePrm.biasOnly = true;
        
        ObsFeatureConjoiner ofc = new ObsFeatureConjoiner(new ObsFeatureConjoinerPrm(), fts);
        JointNlpFgExamplesBuilder builder = new JointNlpFgExamplesBuilder(prm, ofc, cs);
        FgExampleList data = builder.getData(sents);
        ofc.init(data);
        
        System.out.println("Num features: " + fts.getNumObsFeats());
        FgModel model = new FgModel(ofc.getNumParams());

        FgInferencerFactory infFactory = getInfFactory(s);        
        LFgExample ex = data.get(0);
        
        FactorGraph fgLat = CrfObjective.getFgLat(ex.getFgLatPred(), ex.getGoldConfig());
        fgLat.updateFromModel(model);
        FgInferencer infLat = infFactory.getInferencer(fgLat);
        infLat.run();        
        assertEquals(2, infLat.getPartition(), 2);
        // Check that the partition function is computed identically for each variable.
        for (Var v : fgLat.getVars()) {
            double partition = ((BeliefPropagation)infLat).getPartitionBeliefAtVarNode(fgLat.getNode(v));
            //TODO: assertEquals(2, logDomain ? FastMath.exp(partition) : partition, 1e-3);
        }
        
        System.out.println("-------- Running LatPred Inference-----------");
        
        FactorGraph fgLatPred = ex.getFgLatPred();
        fgLatPred.updateFromModel(model);
        FgInferencer infLatPred = infFactory.getInferencer(fgLatPred);
        infLatPred.run();        
        // 2 trees, and 3 different roles (including argUNK)
        assertEquals(2*3, infLatPred.getPartition(), 1e-3);         

        // Print schedule:
        BfsMpSchedule schedule = new BfsMpSchedule(fgLatPred);        
        System.out.println();
        for (Object edge : schedule.getOrder()) {
            System.out.println(edge.toString());
        }
        System.out.println();
        // Print factors
        for (Factor f : fgLatPred.getFactors()) {
            System.out.println(f);
        }
        // Check that the partition function is computed identically for each variable.
        for (Var v : fgLatPred.getVars()) {
            double partition = ((BeliefPropagation)infLatPred).getPartitionBeliefAtVarNode(fgLatPred.getNode(v));
            System.out.format("Var=%s partition=%.4f\n", v.toString(), partition);
            assertEquals(2*3, s == LogSemiring.LOG_SEMIRING ? FastMath.exp(partition) : partition, 1e-3);
        }
        
        Function obj = getCrfObj(model, data, infFactory);
        //CrfObjective obj = new CrfObjective(new CrfObjectivePrm(), model, data, infFactory);
        //obj.setPoint(FgModelTest.getParams(model));
        double ll = obj.getValue(model.getParams());        
        assertEquals(2./6., FastMath.exp(ll), 1e-13);
    }
    
    @Test
    public void testDp1stOrderLogLikelihoodLessThanZero() throws Exception {
        checkDp1stOrderLogLikelihoodLessThanZero(RealAlgebra.REAL_ALGEBRA);
        checkDp1stOrderLogLikelihoodLessThanZero(LogSemiring.LOG_SEMIRING);
    }
    
    public void checkDp1stOrderLogLikelihoodLessThanZero(Algebra s) throws Exception {
        Prng.seed(123456789101112l);
        FactorTemplateList fts = new FactorTemplateList();
        ObsFeatureConjoiner ofc = new ObsFeatureConjoiner(new ObsFeatureConjoinerPrm(), fts);

        FgExampleList data = getDp1stOrderData(ofc);
        
        System.out.println("Num features: " + ofc.getNumParams());
        FgModel model = new FgModel(ofc.getNumParams());
        model.setRandomStandardNormal();
        System.out.println("Model L2 norm: " + model.l2Norm());
        
        FgInferencerFactory infFactory = getInfFactory(s); 
        Function obj = getCrfObj(model, data, infFactory);
        double ll = obj.getValue(model.getParams());        
        assertTrue(ll < 0d);
        assertEquals(-15.006, ll, 1e-3);
    }

    public static FgExampleList getDp1stOrderData(ObsFeatureConjoiner ofc) throws IOException {
        AnnoSentenceReaderPrm rPrm = new AnnoSentenceReaderPrm();
        rPrm.maxNumSentences = 3;
        rPrm.maxSentenceLength = 7;
        rPrm.useCoNLLXPhead = true;
        AnnoSentenceReader r = new AnnoSentenceReader(rPrm);
        r.loadSents(CrfObjectiveTest.class.getResourceAsStream(conllXExample), DatasetType.CONLL_X);
        
        CorpusStatisticsPrm csPrm = new CorpusStatisticsPrm();
        CorpusStatistics cs = new CorpusStatistics(csPrm);
        AnnoSentenceCollection sents = r.getData();
        cs.init(sents);
        
        JointNlpFgExampleBuilderPrm prm = new JointNlpFgExampleBuilderPrm();
        prm.fgPrm.includeSrl = false;
        prm.fgPrm.dpPrm.linkVarType = VarType.PREDICTED;
        prm.fgPrm.dpPrm.useProjDepTreeFactor = true;
        prm.exPrm.cacheType = CacheType.NONE;
        
        JointNlpFgExamplesBuilder builder = new JointNlpFgExamplesBuilder(prm, ofc, cs);
        FgExampleList data = builder.getData(sents);
        return data;
    }

    // TODO: This (slow) test exposes a bug:
    // 
    //    Model L2 norm: 8723053.171453144
    //    9547     WARN  CrfObjective - Log-likelihood for example should be <= 0: 4314.9944514715135
    //    9817     WARN  CrfObjective - Log-likelihood for example should be <= 0: 1280.385933105526
    //    10252    WARN  CrfObjective - Log-likelihood for example should be <= 0: 216.61074154658888
    //    10856    INFO  AvgBatchObjective - Average objective for full dataset: -1320.3962741774715
    @Test
    public void testDp2ndOrderBetheFreeEnergy() throws Exception {
        checkDp2ndOrderBetheFreeEnergy(RealAlgebra.REAL_ALGEBRA);
        // checkDp2ndOrderBetheFreeEnergy(Algebras.SPLIT_ALGEBRA);
        // The shifted real algebra gives invalid BFE, it's still not clear if this is just a
        // precision problem or actually a bug.
        //checkDp2ndOrderBetheFreeEnergy(Algebras.SHIFTED_REAL_ALGEBRA);
        checkDp2ndOrderBetheFreeEnergy(LogSemiring.LOG_SEMIRING);
        checkDp2ndOrderBetheFreeEnergy(LogSignAlgebra.LOG_SIGN_ALGEBRA);
    }
    
    public void checkDp2ndOrderBetheFreeEnergy(Algebra s) throws Exception {
        Prng.seed(123456789101112l);
        Pair<FgExampleList, ObsFeatureConjoiner> pair = getDp2ndOrderData(-1);
        FgExampleList data = pair.get1();
        ObsFeatureConjoiner ofc = pair.get2();
        
        System.out.println("Num features: " + ofc.getNumParams());
        FgModel model = new FgModel(ofc.getNumParams());
        model.setRandomStandardNormal();
        model.scale(0.1);
        System.out.println("Model L2 norm: " + model.l2Norm());
        
        ErmaBpPrm bpPrm = new ErmaBpPrm();
        bpPrm.s = s;
        bpPrm.updateOrder = BpUpdateOrder.PARALLEL;
        bpPrm.normalizeMessages = true;
        bpPrm.maxIterations = 50;    
        // Uncomment to enable dumping of beliefs.
        // bpPrm.dumpDir = Paths.get("./tmp/dump" + s.toString());
        // Files.deleteRecursively(bpPrm.dumpDir.toFile());
        FgInferencerFactory infFactory = bpPrm;
        AvgBatchObjective obj = getCrfObj(model, data, infFactory);
        double ll = 0;
        for (int i=0; i<obj.getNumExamples(); i++) {
            double exll = obj.getValue(model.getParams(), new int[]{i});
            System.out.printf("Example %4d ll=%f\n", i, exll);
            //assertTrue(exll <= 0);
            ll += exll;
        }
        assertTrue(ll < 0d);
        //Without scaling: assertEquals(-74.29, ll, 1e-2);
        assertEquals(-10.681, ll, 1e-2);
    }
    
    @Test
    public void testDp2ndOrderGradient() throws Exception {
        Prng.seed(123456789101112l);
        int featureHashMod = 20;
        FgModel model = new FgModel(2*featureHashMod);
        model.setRandomStandardNormal();
        model.scale(0.1);
        
        {
            // Take one gradient step.
            IntDoubleVector gradReal = getGradientDp2ndOrder(model, RealAlgebra.REAL_ALGEBRA, featureHashMod);
            gradReal.scale(0.05);
            model.getParams().add(gradReal);
        }
        
        // Get the gradient using different semirings.
        IntDoubleVector gradReal = getGradientDp2ndOrder(model, RealAlgebra.REAL_ALGEBRA, featureHashMod);
        IntDoubleVector gradSplit = getGradientDp2ndOrder(model, SplitAlgebra.SPLIT_ALGEBRA, featureHashMod);
        // The shifted algebra sometimes gives invalid gradients but might be due to loss of precision.
        IntDoubleVector gradShifted = getGradientDp2ndOrder(model, ShiftedRealAlgebra.SHIFTED_REAL_ALGEBRA, featureHashMod);
        IntDoubleVector gradLog = getGradientDp2ndOrder(model, LogSemiring.LOG_SEMIRING, featureHashMod);
        IntDoubleVector gradLogSign = getGradientDp2ndOrder(model, LogSignAlgebra.LOG_SIGN_ALGEBRA, featureHashMod);

        // Assert that the gradients are all equal.
        for (int i=0; i<featureHashMod; i++) {
            System.out.printf("i=%d gradReal=%.4e gradLog=%.4e gradLogSign=%.4e\n", i, gradReal.get(i), gradLog.get(i), gradLogSign.get(i));
            assertEquals(gradReal.get(i), gradSplit.get(i), 1e-4);
            //assertEquals(gradReal.get(i), gradShifted.get(i), 1e-8);
            assertEquals(gradReal.get(i), gradLog.get(i), 1e-8);
            assertEquals(gradReal.get(i), gradLogSign.get(i), 1e-8);
            assertEquals(gradLog.get(i), gradLogSign.get(i), 1e-8);
        }
    }
    
    public IntDoubleVector getGradientDp2ndOrder(FgModel model, Algebra s, int featureHashMod) throws Exception {
        Pair<FgExampleList, ObsFeatureConjoiner> pair = getDp2ndOrderData(featureHashMod);
        FgExampleList data = pair.get1();
        ObsFeatureConjoiner ofc = pair.get2();
        
        ErmaBpPrm bpPrm = new ErmaBpPrm();
        bpPrm.s = s;
        bpPrm.updateOrder = BpUpdateOrder.PARALLEL;
        bpPrm.normalizeMessages = true;
        bpPrm.maxIterations = 5;
        FgInferencerFactory infFactory = bpPrm;
        AvgBatchObjective obj = getCrfObj(model, data, infFactory);
        return obj.getGradient(model.getParams());
    }
    
    public Pair<FgExampleList, ObsFeatureConjoiner> getDp2ndOrderData(int featureHashMod) {
        AnnoSentenceReaderPrm rPrm = new AnnoSentenceReaderPrm();
        rPrm.maxNumSentences = 3;
        rPrm.maxSentenceLength = 7;
        rPrm.useCoNLLXPhead = true;
        AnnoSentenceReader r = new AnnoSentenceReader(rPrm);
        try {
            r.loadSents(CrfObjectiveTest.class.getResourceAsStream(conllXExample), DatasetType.CONLL_X);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        CorpusStatisticsPrm csPrm = new CorpusStatisticsPrm();
        CorpusStatistics cs = new CorpusStatistics(csPrm);
        AnnoSentenceCollection sents = r.getData();
        cs.init(sents);
        
        FactorTemplateList fts = new FactorTemplateList();
        JointNlpFgExampleBuilderPrm prm = new JointNlpFgExampleBuilderPrm();
        prm.fgPrm.includeSrl = false;
        prm.fgPrm.dpPrm.linkVarType = VarType.PREDICTED;
        prm.fgPrm.dpPrm.useProjDepTreeFactor = true;
        //prm.fgPrm.dpPrm.grandparentFactors = true;
        prm.fgPrm.dpPrm.arbitrarySiblingFactors = true;
        prm.fePrm.dpFePrm.featureHashMod = featureHashMod;
        //prm.fePrm.dpFePrm.firstOrderTpls = TemplateSets.getFromResource(TemplateSets.mcdonaldDepFeatsResource);
        prm.exPrm.cacheType = CacheType.NONE;
        
        ObsFeatureConjoiner ofc = new ObsFeatureConjoiner(new ObsFeatureConjoinerPrm(), fts);
        JointNlpFgExamplesBuilder builder = new JointNlpFgExamplesBuilder(prm, ofc, cs);
        FgExampleList data = builder.getData(sents);
        ofc.init(data);
        return new Pair<FgExampleList, ObsFeatureConjoiner>(data, ofc);
    }
    
    public static AvgBatchObjective getCrfObj(FgModel model, FgExampleList data, FgInferencerFactory infFactory) {
        CrfObjective exObj = new CrfObjective(data, infFactory);
        return new AvgBatchObjective(exObj, model, 1);
    }

    public static FgInferencerFactory getInfFactory(Algebra s) {
        BeliefPropagationPrm bpPrm = new BeliefPropagationPrm();
        bpPrm.s = s;
        bpPrm.schedule = BpScheduleType.TREE_LIKE;
        bpPrm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        bpPrm.normalizeMessages = false;
        bpPrm.maxIterations = 1;        
        return bpPrm;
    }
    
}
