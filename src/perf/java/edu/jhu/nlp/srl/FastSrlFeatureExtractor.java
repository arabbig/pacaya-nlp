package edu.jhu.nlp.srl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.nlp.CorpusStatistics;
import edu.jhu.nlp.ObsFeTypedFactor;
import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.data.simple.IntAnnoSentence;
import edu.jhu.nlp.depparse.BitshiftDepParseFeatures;
import edu.jhu.nlp.depparse.BitshiftDepParseFeatures.FeatureCollection;
import edu.jhu.nlp.srl.SrlFactorGraphBuilder.RoleVar;
import edu.jhu.nlp.srl.SrlFactorGraphBuilder.SenseVar;
import edu.jhu.nlp.srl.SrlFactorGraphBuilder.SrlFactorTemplate;
import edu.jhu.pacaya.gm.data.UFgExample;
import edu.jhu.pacaya.gm.feat.FactorTemplateList;
import edu.jhu.pacaya.gm.feat.FeatureVector;
import edu.jhu.pacaya.gm.feat.ObsFeExpFamFactor;
import edu.jhu.pacaya.gm.feat.ObsFeatureExtractor;
import edu.jhu.pacaya.gm.model.Var;
import edu.jhu.pacaya.gm.model.VarSet;
import edu.jhu.pacaya.util.FeatureNames;

public class FastSrlFeatureExtractor implements ObsFeatureExtractor {

    private static final Logger log = LoggerFactory.getLogger(FastSrlFeatureExtractor.class);     

    private IntAnnoSentence isent;
    private FactorTemplateList fts;
    private int featureHashMod;
    
    public FastSrlFeatureExtractor(AnnoSentence sent, CorpusStatistics cs, int featureHashMod, FactorTemplateList fts) {
        this.isent = new IntAnnoSentence(sent, cs.store);
        this.featureHashMod = featureHashMod;
        this.fts = fts;
    }

    @Override
    public void init(UFgExample ex, FactorTemplateList fts) {
        this.fts = fts;
    }
    
    @Override
    public FeatureVector calcObsFeatureVector(ObsFeExpFamFactor factor) {
        ObsFeTypedFactor f = (ObsFeTypedFactor) factor;
        FeatureNames alphabet = fts.getTemplate(f).getAlphabet();
        Enum<?> ft = f.getFactorType();
        VarSet vars = f.getVars();
                
        FeatureVector feats = new FeatureVector();
        if (ft == SrlFactorTemplate.ROLE_UNARY || ft == SrlFactorTemplate.SENSE_ROLE_BINARY) {
            // Look at the variables to determine the parent and child.
            Var var = vars.iterator().next();
            int parent = ((RoleVar)var).getParent();
            int child = ((RoleVar)var).getChild();
            BitshiftDepParseFeatures.addArcFactoredMSTFeats(isent, parent, child, FeatureCollection.ARC, feats, false, false, featureHashMod);            
        } else if (ft == SrlFactorTemplate.SENSE_UNARY) {
            SenseVar var = (SenseVar) vars.iterator().next();
            int parent = var.getParent();
            BitshiftDepParseFeatures.addArcFactoredMSTFeats(isent, -1, parent, FeatureCollection.ARC, feats, false, false, featureHashMod);
        } else {
            throw new RuntimeException("Unsupported template: " + ft);
        }
        int[] idxs = feats.getInternalIndices();
        //double[] vals = feats.getInternalValues();
        int used = feats.getUsed();
        for (int i=0; i<used; i++) {
            idxs[i] = alphabet.lookupIndex(idxs[i]);
        }
        return feats;
    }
        
}
