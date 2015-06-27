package edu.jhu.nlp.srl;

import edu.jhu.nlp.CorpusStatistics;
import edu.jhu.nlp.data.conll.SrlGraph;
import edu.jhu.nlp.data.conll.SrlGraph.SrlEdge;
import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.srl.SrlFactorGraphBuilder.RoleVar;
import edu.jhu.nlp.srl.SrlFactorGraphBuilder.SenseVar;
import edu.jhu.nlp.srl.SrlFactorGraphBuilder.SrlFactorGraphBuilderPrm;
import edu.jhu.nlp.srl.SrlFeatureExtractor.SrlFeatureExtractorPrm;
import edu.jhu.pacaya.gm.app.Encoder;
import edu.jhu.pacaya.gm.data.LFgExample;
import edu.jhu.pacaya.gm.data.LabeledFgExample;
import edu.jhu.pacaya.gm.data.UFgExample;
import edu.jhu.pacaya.gm.data.UnlabeledFgExample;
import edu.jhu.pacaya.gm.feat.FactorTemplateList;
import edu.jhu.pacaya.gm.feat.ObsFeatureCache;
import edu.jhu.pacaya.gm.feat.ObsFeatureConjoiner;
import edu.jhu.pacaya.gm.feat.ObsFeatureExtractor;
import edu.jhu.pacaya.gm.model.FactorGraph;
import edu.jhu.pacaya.gm.model.Var;
import edu.jhu.pacaya.gm.model.VarConfig;
import edu.jhu.pacaya.gm.model.Var.VarType;
import edu.jhu.prim.set.IntHashSet;

/**
 * Encodes an {@link AnnoSentence} as a semantic role labeling factor graph and its training
 * variable assignment.
 * 
 * @author mgormley
 */
public class SrlEncoder implements Encoder<AnnoSentence, SrlGraph> {

    // TODO: Use this in JointNlp
    public static class SrlEncoderPrm {
        public SrlFactorGraphBuilderPrm srlPrm = new SrlFactorGraphBuilderPrm();
        public SrlFeatureExtractorPrm srlFePrm = new SrlFeatureExtractorPrm();        
    }
    
    private SrlEncoderPrm prm;
    private CorpusStatistics cs;
    private ObsFeatureConjoiner ofc;
    
    public SrlEncoder(SrlEncoderPrm prm, CorpusStatistics cs, ObsFeatureConjoiner ofc) {
        this.prm = prm;
        this.cs = cs;
        this.ofc = ofc;
    }

    @Override
    public LFgExample encode(AnnoSentence sent, SrlGraph graph) {
        return getExample(sent, graph, true);
    }

    @Override
    public UFgExample encode(AnnoSentence sent) {
        return getExample(sent, null, false);
    }

    private LFgExample getExample(AnnoSentence sent, SrlGraph graph, boolean labeledExample) {
        // Create a feature extractor for this example.
        //ObsFeatureExtractor obsFe = new FastSrlFeatureExtractor(sent, cs, prm.srlFePrm.featureHashMod, ofc.getTemplates());
        ObsFeatureExtractor obsFe = new SrlFeatureExtractor(prm.srlFePrm, sent, cs);
        obsFe = new ObsFeatureCache(obsFe);
        
        FactorGraph fg = new FactorGraph();
        SrlFactorGraphBuilder srl = new SrlFactorGraphBuilder(prm.srlPrm);
        srl.build(sent, cs, obsFe, ofc, fg);
        
        VarConfig goldConfig = new VarConfig();
        if (labeledExample) {
            addSrlTrainAssignment(sent, graph, srl, goldConfig, prm.srlPrm.predictSense, prm.srlPrm.predictPredPos);
        }

        FactorTemplateList fts = ofc.getTemplates();
        if (labeledExample) {
            return new LabeledFgExample(fg, goldConfig, obsFe, fts);
        } else {
            return new UnlabeledFgExample(fg, obsFe, fts);
        }
    }
    
    // TODO: Consider passing in the knownPreds from AnnoSentence.
    public static void addSrlTrainAssignment(AnnoSentence sent, SrlGraph srlGraph, SrlFactorGraphBuilder sfg, VarConfig vc, boolean predictSense, boolean predictPredPos) {
        // ROLE VARS
        // Add all the training data assignments to the role variables, if they are not latent.
        // First, just set all the role names to "_".
        for (int i=0; i<sent.size(); i++) {
            for (int j=0; j<sent.size(); j++) {
                RoleVar roleVar = sfg.getRoleVar(i, j);
                if (roleVar != null && roleVar.getType() != VarType.LATENT) {
                    vc.put(roleVar, "_");
                }
            }
        }
        // Then set the ones which are observed.
        for (SrlEdge edge : srlGraph.getEdges()) {
            int parent = edge.getPred().getPosition();
            int child = edge.getArg().getPosition();
            String roleName = edge.getLabel();
            
            RoleVar roleVar = sfg.getRoleVar(parent, child);
            if (roleVar != null && roleVar.getType() != VarType.LATENT) {
                int roleNameIdx = roleVar.getState(roleName);
                // TODO: This isn't quite right...we should really store the actual role name here.
                if (roleNameIdx == -1) {
                    vc.put(roleVar, CorpusStatistics.UNKNOWN_ROLE);
                } else {
                    vc.put(roleVar, roleNameIdx);
                }
            }
        }
        
        // Add the training data assignments to the predicate senses.
        IntHashSet knownPreds = srlGraph.getKnownPreds();
        for (int i=0; i<sent.size(); i++) {
            SenseVar senseVar = sfg.getSenseVar(i);
            if (senseVar != null) {
                if (!predictSense && !predictPredPos) {
                    throw new IllegalStateException("Neither predictSense nor predictPredPos is set. So there shouldn't be any SenseVars.");
                }
                if (knownPreds.contains(i)) {
                    if (predictSense) {
                        // Tries to map the sense variable to its label (e.g. argM-TMP).
                        // If the variable state space does not include that label, we
                        // fall back on the UNKNOWN_SENSE constant. If for some reason
                        // the UNKNOWN_SENSE constant isn't present, we just set it to the
                        // first possible sense.
                        if (!tryPut(vc, senseVar, srlGraph.getPredAt(i).getLabel())) {
                            if (!tryPut(vc, senseVar, CorpusStatistics.UNKNOWN_SENSE)) {
                                // This is a hack to ensure that something is added at test time.
                                vc.put(senseVar, 0);
                            }
                        }
                    } else { // (predictPredPos && !predictPredSense)
                        // We use CorpusStatistics.UNKNOWN_SENSE to indicate that
                        // there exists a predicate at this position.
                        vc.put(senseVar, CorpusStatistics.UNKNOWN_SENSE);   
                    }
                } else {
                    // The "_" indicates that there is no predicate at this
                    // position.
                    vc.put(senseVar, "_");
                }
            }
        }
    }

    /**
     * Trys to put the entry (var, stateName) in vc.
     * @return True iff the entry (var, stateName) was added to vc.
     */
    private static boolean tryPut(VarConfig vc, Var var, String stateName) {
        int stateNameIdx = var.getState(stateName);
        if (stateNameIdx == -1) {
            return false;
        } else {
            vc.put(var, stateName);
            return true;
        }
    }
    
}
