package edu.jhu.nlp.srl;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.jhu.nlp.CorpusStatistics;
import edu.jhu.nlp.CorpusStatistics.CorpusStatisticsPrm;
import edu.jhu.nlp.data.DepEdgeMask;
import edu.jhu.nlp.data.conll.CoNLL09FileReader;
import edu.jhu.nlp.data.conll.CoNLL09ReadWriteTest;
import edu.jhu.nlp.data.conll.CoNLL09Sentence;
import edu.jhu.nlp.data.conll.CoNLL09Token;
import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.data.simple.AnnoSentenceCollection;
import edu.jhu.nlp.features.TemplateSets;
import edu.jhu.nlp.joint.JointNlpFactorGraph;
import edu.jhu.nlp.joint.JointNlpFactorGraph.JointNlpFactorGraphPrm;
import edu.jhu.nlp.joint.JointNlpFgExamplesBuilder;
import edu.jhu.nlp.joint.JointNlpFgExamplesBuilder.JointNlpFgExampleBuilderPrm;
import edu.jhu.nlp.srl.SrlFactorGraphBuilder.RoleStructure;
import edu.jhu.nlp.srl.SrlFeatureExtractor.SrlFeatureExtractorPrm;
import edu.jhu.pacaya.gm.data.FgExampleList;
import edu.jhu.pacaya.gm.feat.FactorTemplateList;
import edu.jhu.pacaya.gm.feat.FeatureExtractor;
import edu.jhu.pacaya.gm.feat.ObsFeExpFamFactor;
import edu.jhu.pacaya.gm.feat.ObsFeatureConjoiner;
import edu.jhu.pacaya.gm.feat.ObsFeatureConjoiner.ObsFeatureConjoinerPrm;
import edu.jhu.pacaya.gm.feat.ObsFeatureExtractor;
import edu.jhu.pacaya.gm.model.Factor;
import edu.jhu.pacaya.gm.model.Var.VarType;
import edu.jhu.pacaya.gm.train.SimpleVCFeatureExtractor;
import edu.jhu.pacaya.gm.train.SimpleVCObsFeatureExtractor;
import edu.jhu.pacaya.util.FeatureNames;
import edu.jhu.pacaya.util.collections.QLists;
import edu.jhu.prim.set.IntHashSet;

/**
 * Unit tests for {@link SrlFeatureExtractorTest}.
 * @author mgormley
 */
public class SrlFeatureExtractorTest {

    @Test
    public void testCorrectNumFeatures() throws Exception {
        JointNlpFactorGraphPrm fgPrm = new JointNlpFactorGraphPrm();
        fgPrm.srlPrm.predictPredPos = true;
        fgPrm.srlPrm.binarySenseRoleFactors = true;
        fgPrm.includeRel = false;
        JointNlpFactorGraph sfg = getSrlFg(fgPrm);

        FactorTemplateList fts = new FactorTemplateList();
        
        InputStream inputStream = this.getClass().getResourceAsStream(CoNLL09ReadWriteTest.conll2009Example);
        CoNLL09FileReader cr = new CoNLL09FileReader(inputStream);
        CorpusStatisticsPrm csPrm = new CorpusStatisticsPrm();
        AnnoSentenceCollection sents = CoNLL09Sentence.toAnno(cr.readSents(1), csPrm.useGoldSyntax);
        CorpusStatistics cs = new CorpusStatistics(csPrm);
        cs.init(sents);
        
        fts.lookupTemplateIds(sfg);
        
        SrlFeatureExtractorPrm prm = new SrlFeatureExtractorPrm();
        prm.biasOnly = true;
        prm.featureHashMod = -1; // Disable feature hashing.
        SrlFeatureExtractor featExt = new SrlFeatureExtractor(prm, sents.get(0), cs, fts);
        featExt.init(fts);
        for (int a=0; a<sfg.getNumFactors(); a++) {
            Factor f = sfg.getFactor(a);
            if (f instanceof ObsFeExpFamFactor) {
                featExt.calcObsFeatureVector((ObsFeExpFamFactor) f);
            }
        }
        
        System.out.println(fts);
        
        assertEquals(4, fts.size());
        assertEquals(4, fts.getNumObsFeats());
    }
    
    @Test
    public void testCorrectNumExpandedFeatures() throws Exception {
        // What's up with this one?
        FactorTemplateList fts = new FactorTemplateList();

        InputStream inputStream = this.getClass().getResourceAsStream(CoNLL09ReadWriteTest.conll2009Example);
        CoNLL09FileReader cr = new CoNLL09FileReader(inputStream);
        CorpusStatisticsPrm csPrm = new CorpusStatisticsPrm();
        List<CoNLL09Sentence> conllSents = cr.readSents(1, 20);
        AnnoSentenceCollection sents = new AnnoSentenceCollection();
        for (CoNLL09Sentence sent : conllSents) {
            sent.normalizeRoleNames();
            AnnoSentence simpleSent = sent.toAnnoSentence(csPrm.useGoldSyntax);
            sents.add(simpleSent);
        }
        CorpusStatistics cs = new CorpusStatistics(csPrm);
        cs.init(sents);

        JointNlpFgExampleBuilderPrm prm = new JointNlpFgExampleBuilderPrm();        
        prm.fgPrm.srlPrm.srlFePrm.featureHashMod = -1;
        
        prm.fgPrm.srlPrm.roleStructure = RoleStructure.PREDS_GIVEN;
        prm.fgPrm.dpPrm.linkVarType = VarType.PREDICTED;
        prm.fgPrm.includeRel = false;

        ObsFeatureConjoiner ofc = new ObsFeatureConjoiner(new ObsFeatureConjoinerPrm(), fts);
        JointNlpFgExamplesBuilder builder = new JointNlpFgExamplesBuilder(prm, ofc, cs);
        FgExampleList data = builder.getData(sents);
        
        System.out.println("Num tokens: " + sents.get(0).size());
        System.out.println(ofc);
        
        // If we included all features we would get: 6*2 + 2 + 6
        // For biasOnly=true: 
        //assertEquals(17, model.getAlphabet().size());
        
        assertEquals(2920, ofc.getNumParams());
    }
    
    @Test
    public void testCorrectNumExpandedFeaturesForSmallSent() throws Exception {
        FactorTemplateList fts = new FactorTemplateList();
        List<CoNLL09Token> tokens = new ArrayList<CoNLL09Token>();
        //tokens.add(new CoNLL09Token(id, form, lemma, plemma, pos, ppos, feat, pfeat, head, phead, deprel, pdeprel, fillpred, pred, apreds));
        //tokens.add(new CoNLL09Token(1, "the", "_", "_", "Det", "_", getList("feat"), getList("feat") , 2, 2, "det", "_", false, "_", getList("_")));
        tokens.add(new CoNLL09Token(2, "dog", "_", "_", "N", "_", QLists.getList("feat"), QLists.getList("feat") , 3, 3, "subj", "_", false, "_", QLists.getList("arg0")));
        tokens.add(new CoNLL09Token(3, "ate", "_", "_", "V", "_", QLists.getList("feat"), QLists.getList("feat") , 0, 0, "v", "_", true, "ate.1", QLists.getList("_")));
        tokens.add(new CoNLL09Token(4, "food", "_", "_", "N", "_", QLists.getList("feat"), QLists.getList("feat") , 2, 2, "obj", "_", false, "_", QLists.getList("arg1")));
        CoNLL09Sentence sent = new CoNLL09Sentence(tokens);
        
        List<CoNLL09Sentence> sents = QLists.getList(sent);
        AnnoSentenceCollection simpleSents = new AnnoSentenceCollection();
        CorpusStatisticsPrm csPrm = new CorpusStatisticsPrm();
        CorpusStatistics cs = new CorpusStatistics(csPrm);
        for (CoNLL09Sentence s : sents) {
            s.normalizeRoleNames();
            simpleSents.add(s.toAnnoSentence(csPrm.useGoldSyntax));
        }
        cs.init(simpleSents);

        JointNlpFgExampleBuilderPrm prm = new JointNlpFgExampleBuilderPrm();        
        prm.fgPrm.srlPrm.srlFePrm.featureHashMod = -1;
        
        prm.fgPrm.srlPrm.roleStructure = RoleStructure.PREDS_GIVEN;
        prm.fgPrm.dpPrm.linkVarType = VarType.PREDICTED;
        prm.fgPrm.includeRel = false;
        
        ObsFeatureConjoiner ofc = new ObsFeatureConjoiner(new ObsFeatureConjoinerPrm(), fts);
        JointNlpFgExamplesBuilder builder = new JointNlpFgExamplesBuilder(prm, ofc, cs);
        FgExampleList data = builder.getData(simpleSents);
        
        System.out.println("Num tokens: " + sents.get(0).size());
        System.out.println(ofc);
        // If we included all features we would get: 6*2 + 2 + 6
        // For biasOnly=true: 
        // assertEquals(17, model.getAlphabet().size());
        
        // For useNaradFeats=true: 
        // Correct number is 72, and seeing 72 after bad commit.
        assertEquals(170, ofc.getNumParams());
    }
    
    @Test
    public void testCorrectNumFeaturesWithFeatureHashing() throws Exception {
        JointNlpFactorGraphPrm fgPrm = new JointNlpFactorGraphPrm();
        fgPrm.includeRel = false;
        JointNlpFactorGraph sfg = getSrlFg(fgPrm);

        FactorTemplateList fts = new FactorTemplateList();        

        InputStream inputStream = this.getClass().getResourceAsStream(CoNLL09ReadWriteTest.conll2009Example);
        CoNLL09FileReader cr = new CoNLL09FileReader(inputStream);
        List<CoNLL09Sentence> sents = cr.readSents(1);

        AnnoSentenceCollection simpleSents = new AnnoSentenceCollection();
        CorpusStatisticsPrm csPrm = new CorpusStatisticsPrm();
        CorpusStatistics cs = new CorpusStatistics(csPrm);
        for (CoNLL09Sentence s : sents) {
            s.normalizeRoleNames();
            simpleSents.add(s.toAnnoSentence(csPrm.useGoldSyntax));
        }
        cs.init(simpleSents);
        
        
        fts.lookupTemplateIds(sfg);
        
        SrlFeatureExtractorPrm prm = new SrlFeatureExtractorPrm();
        prm.useTemplates = true;
        prm.soloTemplates = TemplateSets.getNaradowskySenseUnigramFeatureTemplates();
        prm.pairTemplates = TemplateSets.getNaradowskyArgUnigramFeatureTemplates();
        prm.featureHashMod = 2; // Enable feature hashing
        SrlFeatureExtractor featExt = new SrlFeatureExtractor(prm, simpleSents.get(0), cs, fts);
        featExt.init(fts);
        for (int a=0; a<sfg.getNumFactors(); a++) {
            Factor f = sfg.getFactor(a);
            if (f instanceof ObsFeExpFamFactor) {
                featExt.calcObsFeatureVector((ObsFeExpFamFactor) f);
            }
        }
        
        System.out.println(fts);
        // We should include some bias features as well.
        assertEquals(4, fts.getNumObsFeats());
    }
    

    private static JointNlpFactorGraph getSrlFg(JointNlpFactorGraphPrm prm) {
        // --- These won't even be used in these tests ---
        FactorTemplateList fts = new FactorTemplateList();
        ObsFeatureConjoiner ofc = new ObsFeatureConjoiner(new ObsFeatureConjoinerPrm(), fts);
        // ---        
        IntHashSet knownPreds = IntHashSet.fromArray(0, 2);
        List<String> words = QLists.getList("w1", "w2", "w3");
        DepEdgeMask depEdgeMask = new DepEdgeMask(words.size(), true);
        
        AnnoSentence sent = new AnnoSentence();
        sent.setWords(words);
        sent.setLemmas(words);
        sent.setKnownPreds(knownPreds);
        sent.setDepEdgeMask(depEdgeMask);
        
        CorpusStatistics cs = new CorpusStatistics(new CorpusStatisticsPrm());
        cs.roleStateNames = QLists.getList("A1", "A2", "A3");
        
        prm.srlPrm.srlFePrm.biasOnly = true;
        prm.dpPrm.dpFePrm.biasOnly = true;
        return new JointNlpFactorGraph(prm, sent, cs, ofc);
    }

}
