package edu.jhu.nlp.relations;

import java.util.ArrayList;
import java.util.List;

import edu.jhu.nlp.data.simple.AnnoSentence;
import edu.jhu.nlp.relations.RelationsFactorGraphBuilder.RelVar;
import edu.jhu.pacaya.gm.app.Decoder;
import edu.jhu.pacaya.gm.data.UFgExample;
import edu.jhu.pacaya.gm.decode.MbrDecoder;
import edu.jhu.pacaya.gm.decode.MbrDecoder.MbrDecoderPrm;
import edu.jhu.pacaya.gm.inf.FgInferencer;
import edu.jhu.pacaya.gm.model.Var;
import edu.jhu.pacaya.gm.model.VarConfig;

public class RelationsDecoder implements Decoder<AnnoSentence, List<String>> {
    
    public static class RelationsDecoderPrm {
        // TODO: Set to non-null values.
        public MbrDecoderPrm mbrPrm = null;
    }
    
    private RelationsDecoderPrm prm;
    
    public RelationsDecoder(RelationsDecoderPrm prm) {
        this.prm = prm;
    }
    
    @Override
    public List<String> decode(FgInferencer inf, UFgExample ex, AnnoSentence sent) {
        MbrDecoder mbrDecoder = new MbrDecoder(prm.mbrPrm);
        mbrDecoder.decode(inf, ex);
        VarConfig mbrVarConfig = mbrDecoder.getMbrVarConfig();
        // Get the Relations graph.
        return RelationsDecoder.getRelLabelsFromVarConfig(mbrVarConfig);
    }

    public static List<String> getRelLabelsFromVarConfig(VarConfig mbrVarConfig) {
        int relVarCount = 0;
        List<String> rels = new ArrayList<>();
        for (Var v : mbrVarConfig.getVars()) {
           if (v instanceof RelVar) {
               RelVar rv = (RelVar) v;
               String relation = mbrVarConfig.getStateName(rv);
               rels.add(relation);
               relVarCount++;
           }
        }

        if (relVarCount > 0) {
            return rels;
        } else {
            return null;
        }        
    }
    
    // These decode methods could be used for decoding to RelationMention objects.
//    @Override
//    public RelationMentions decode(FgInferencer inf, UFgExample ex, AnnoSentence sent) {
//        MbrDecoder mbrDecoder = new MbrDecoder(prm.mbrPrm);
//        mbrDecoder.decode(inf, ex);
//        VarConfig mbrVarConfig = mbrDecoder.getMbrVarConfig();
//        // Get the Relations graph.
//        return RelationsDecoder.getRelationsGraphFromVarConfig(mbrVarConfig);
//    }
//    
//    public static RelationMentions getRelationsGraphFromVarConfig(VarConfig mbrVarConfig) {
//        int relVarCount = 0;
//        RelationMentions rels = new RelationMentions();
//        for (Var v : mbrVarConfig.getVars()) {
//           if (v instanceof RelVar) {
//               RelVar rv = (RelVar) v;
//               String relation = mbrVarConfig.getStateName(rv);
//               String[] splits = relation.split(":");
//               assert splits.length == 3 : Arrays.toString(splits);
//               String type = splits[0];               
//               assert rv.ment1.compareTo(rv.ment2) <= 0;
//               List<Pair<String, NerMention>> args = Lists.getList(
//                       new Pair<String,NerMention>(splits[1], rv.ment1), 
//                       new Pair<String,NerMention>(splits[2], rv.ment2));
//               rels.add(new RelationMention(type, null, args, null));
//               relVarCount++;
//           }
//        }
//
//        if (relVarCount > 0) {
//            return rels;
//        } else {
//            return null;
//        }
//    }

}
