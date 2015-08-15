package edu.jhu.nlp.joint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.nlp.CorpusStatistics;
import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.data.simple.AnnoSentenceCollection;
import edu.jhu.nlp.depparse.BitshiftDepParseFeatureExtractor;
import edu.jhu.nlp.depparse.BitshiftDepParseFeatureExtractor.BitshiftDepParseFeatureExtractorPrm;
import edu.jhu.nlp.depparse.DepParseEncoder;
import edu.jhu.nlp.depparse.DepParseFeatureExtractor;
import edu.jhu.nlp.depparse.DepParseFeatureExtractor.DepParseFeatureExtractorPrm;
import edu.jhu.nlp.features.TemplateLanguage;
import edu.jhu.nlp.joint.JointNlpFactorGraph.JointFactorGraphPrm;
import edu.jhu.nlp.relations.RelObsFe;
import edu.jhu.nlp.relations.RelationsEncoder;
import edu.jhu.nlp.srl.SrlEncoder;
import edu.jhu.nlp.srl.SrlFeatureExtractor;
import edu.jhu.nlp.srl.SrlFeatureExtractor.SrlFeatureExtractorPrm;
import edu.jhu.pacaya.gm.app.Encoder;
import edu.jhu.pacaya.gm.data.LFgExample;
import edu.jhu.pacaya.gm.data.LabeledFgExample;
import edu.jhu.pacaya.gm.data.UFgExample;
import edu.jhu.pacaya.gm.data.UnlabeledFgExample;
import edu.jhu.pacaya.gm.feat.FactorTemplateList;
import edu.jhu.pacaya.gm.feat.FeatureCache;
import edu.jhu.pacaya.gm.feat.FeatureExtractor;
import edu.jhu.pacaya.gm.feat.ObsFeatureCache;
import edu.jhu.pacaya.gm.feat.ObsFeatureConjoiner;
import edu.jhu.pacaya.gm.feat.ObsFeatureExtractor;
import edu.jhu.pacaya.gm.model.VarConfig;
import edu.jhu.pacaya.util.Prm;

/**
 * Encodes a joint NLP factor graph and its variable assignment.
 * @author mgormley
 */
public class JointNlpEncoder implements Encoder<AnnoSentence, AnnoSentence> {

    private static final Logger log = LoggerFactory.getLogger(JointNlpEncoder.class);

    public static class JointNlpEncoderPrm extends Prm {
        private static final long serialVersionUID = 1L;
        public JointFactorGraphPrm fgPrm = new JointFactorGraphPrm();
        public JointNlpFeatureExtractorPrm fePrm = new JointNlpFeatureExtractorPrm();
    }
    
    public static class JointNlpFeatureExtractorPrm extends Prm {
        private static final long serialVersionUID = 1L;
        public DepParseFeatureExtractorPrm dpFePrm = new DepParseFeatureExtractorPrm();
        public BitshiftDepParseFeatureExtractorPrm bsDpFePrm = new BitshiftDepParseFeatureExtractorPrm();
        public SrlFeatureExtractorPrm srlFePrm = new SrlFeatureExtractorPrm();        
    }

    private JointNlpEncoderPrm prm;
    private CorpusStatistics cs;
    private ObsFeatureConjoiner ofc;
    
    public JointNlpEncoder(JointNlpEncoderPrm prm, CorpusStatistics cs, ObsFeatureConjoiner ofc) {
        this.prm = prm;
        this.cs = cs;
        this.ofc = ofc;
    }

    @Override
    public LFgExample encode(AnnoSentence input, AnnoSentence gold) {
        return getExample(input, gold, true);
    }

    @Override
    public UFgExample encode(AnnoSentence input) {
        return getExample(input, null, false);
    }

    private LFgExample getExample(AnnoSentence sent, AnnoSentence gold, boolean labeledExample) {
        // Create a feature extractor for this example.
        // TODO: We should only create the feature extractors for parts of the model we're going to instantiate.
        ObsFeatureExtractor srlFe = new SrlFeatureExtractor(prm.fePrm.srlFePrm, sent, cs);
        srlFe = new ObsFeatureCache(srlFe);        
        FeatureExtractor dpFe = prm.fePrm.dpFePrm.onlyFast ?
                new BitshiftDepParseFeatureExtractor(prm.fePrm.bsDpFePrm, sent, cs, ofc) :
                new DepParseFeatureExtractor(prm.fePrm.dpFePrm, sent, cs, ofc.getFeAlphabet());
        dpFe = new FeatureCache(dpFe);
        
        // Construct the factor graph.
        JointNlpFactorGraph fg = new JointNlpFactorGraph(prm.fgPrm, sent, cs, srlFe, ofc, dpFe);
        log.trace("Number of variables: " + fg.getNumVars() + " Number of factors: " + fg.getNumFactors() + " Number of edges: " + fg.getNumEdges());

        // Get the variable assignments given in the training data.
        VarConfig vc = new VarConfig();
        if (prm.fgPrm.includeDp) {
            if (gold != null && gold.getParents() != null) {
                DepParseEncoder.addDepParseTrainAssignment(gold.getParents(), fg.getDpBuilder(), vc);
            }
        }
        if (prm.fgPrm.includeSrl) {
            if (gold != null && gold.getSrlGraph() != null) {
                SrlEncoder.addSrlTrainAssignment(sent, gold.getSrlGraph(), fg.getSrlBuilder(), vc, prm.fgPrm.srlPrm.predictSense, prm.fgPrm.srlPrm.predictPredPos);
            }
        }
        if (prm.fgPrm.includeRel) {
            if (gold != null && gold.getRelLabels() != null) {
                RelationsEncoder.addRelVarAssignments(sent, gold.getRelLabels(), fg.getRelBuilder(), vc);
            }
        }
        
        // Create the example.
        LFgExample ex;
        FactorTemplateList fts = ofc.getTemplates();
        if (labeledExample) {
            ex = new LabeledFgExample(fg, vc, srlFe, fts);
        } else {
            ex = new UnlabeledFgExample(fg, srlFe, fts);
        }
        dpFe.init(ex);
        return ex;
    }

    public static void checkForRequiredAnnotations(JointNlpEncoderPrm prm, AnnoSentenceCollection sents) {
        try {
            if (sents.size() == 0) { return; }
            // Check that the first sentence has all the required annotation
            // types for the specified feature templates.
            AnnoSentence sent = sents.get(0);
            if (prm.fePrm.srlFePrm.useTemplates) {
                if (prm.fgPrm.includeSrl) {
                    TemplateLanguage.assertRequiredAnnotationTypes(sent, prm.fePrm.srlFePrm.soloTemplates);
                    TemplateLanguage.assertRequiredAnnotationTypes(sent, prm.fePrm.srlFePrm.pairTemplates);
                }
            }
            if (prm.fgPrm.includeDp && !prm.fePrm.dpFePrm.onlyFast) {
                TemplateLanguage.assertRequiredAnnotationTypes(sent, prm.fePrm.dpFePrm.firstOrderTpls);
                if (prm.fgPrm.dpPrm.grandparentFactors || prm.fgPrm.dpPrm.arbitrarySiblingFactors) {
                    TemplateLanguage.assertRequiredAnnotationTypes(sent, prm.fePrm.dpFePrm.secondOrderTpls);
                }
            }
        } catch (IllegalStateException e) {
            log.error(e.getMessage());
            log.trace("", e);
        }
    }
    
}
