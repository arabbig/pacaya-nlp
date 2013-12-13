package edu.jhu.featurize;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import edu.berkeley.nlp.PCFGLA.smoothing.SrlBerkeleySignatureBuilder;
import edu.jhu.data.DepTree.Dir;
import edu.jhu.data.simple.SimpleAnnoSentence;
import edu.jhu.prim.tuple.Pair;
import edu.jhu.srl.CorpusStatistics;

/**
 * Feature extraction from the observations on a particular sentence.
 * 
 * @author mmitchell
 * @author mgormley
 */
public class SentFeatureExtractor {
    /*
    * Treats features as combinations of feature templates, for:
    * 1. word form (formFeats)
    * 2. lemma (lemmaFeats)
    * 3. part-of-speech (tagFeats)
    * 4. morphological features (morphFeats)
    * 5. syntactic dependency label (deprelFeats)
    * 6. children (childrenFeats)
    * 7. dependency paths (pathFeats)
    * 8. 'high' and 'low' support, siblings, parents (syntacticConnectionFeats)
    */
    
    
    //private static final Logger log = Logger.getLogger(SentFeatureExtractor.class);
    public static class SentFeatureExtractorPrm {
        /** For testing only: this will ensure that the only feature returned is the bias feature. */
        public boolean biasOnly = false;
        public boolean isProjective = false;
        /** Switches for feature templates. */
        public boolean useTemplates = false;
        public boolean formFeats = true;
        public boolean lemmaFeats = true;
        public boolean tagFeats = true;
        public boolean morphFeats = true;
        public boolean deprelFeats = true;
        public boolean childrenFeats = true;
        public boolean pathFeats = true;
        public boolean syntacticConnectionFeats = true;
        /** Whether to use supervised features. */
        public boolean withSupervision = true;
        /** Whether to add the "Simple" features. */
        public boolean useSimpleFeats = true;
        /** Whether to add the "Naradowsky" features. */
        public boolean useNaradFeats = true;
        /** Whether to add the "Zhao" features. */
        public boolean useZhaoFeats = true;
        public boolean useDepPathFeats = true;
        // NOTE: We default to false on these features since including these
        // would break a lot of unit tests, which expect the default to be 
        // only Zhao+Narad features.
        /** Whether to add the "Bjorkelund" features. */
        public boolean useBjorkelundFeats = false;
        /** Whether to add all possible features using the templates defined above. **/
        public boolean useAllTemplates = false;
    }
    
    // Parameters for feature extraction.
    private SentFeatureExtractorPrm prm;
    
    private final SimpleAnnoSentence sent;
    private final CorpusStatistics cs;
    private final SrlBerkeleySignatureBuilder sig;
    private final int[] parents;
    private FeaturizedSentence fSent;
        
    public SentFeatureExtractor(SentFeatureExtractorPrm prm, SimpleAnnoSentence sent, CorpusStatistics cs) {
        this.prm = prm;
        this.sent = sent;
        this.cs = cs;
        this.sig = cs.sig;
        if (!prm.biasOnly) {
            // Syntactic parents of all the words in this sentence, in order (idx 0 is -1)
            this.parents = getParents(sent);
            // TBD:  Should this be defined differently?
            if (prm.useZhaoFeats || prm.useDepPathFeats || prm.useBjorkelundFeats) {
                fSent = new FeaturizedSentence(sent);
            }
            if (prm.useAllTemplates) {
                this.prm.formFeats = true;
                this.prm.lemmaFeats = true;
                this.prm.tagFeats = true;
                this.prm.morphFeats = true;
                this.prm.deprelFeats = true;
                this.prm.childrenFeats = true;
                this.prm.pathFeats = true;
                this.prm.syntacticConnectionFeats = true;
            }
        } else {
            this.parents = null;
        }
    }

    public int getSentSize() {
        return sent.size();
    }
    
    // ----------------- Extracting Features on the Observations ONLY -----------------

    // Package private for testing.
    int[] getParents(SimpleAnnoSentence sent) {
        return sent.getParents();
    }

    /**
     * Creates a feature set for the given word position.
     * 
     * This defines a feature function of the form f(x, i), where x is a vector
     * representing all the observations about a sentence and i is a position in
     * the sentence.
     * 
     * Examples where this feature function would be used include a unary factor
     * on a Sense variable in SRL, or a syntactic Link variable where the parent
     * is the "Wall" node.
     * 
     * @param idx The position of a word in the sentence.
     * @return The features.
     */
    public ArrayList<String> createFeatureSet(int idx) {
        ArrayList<String> feats = new ArrayList<String>();
        if (prm.biasOnly) { return feats; }
        
        if (prm.useSimpleFeats) {
            addSimpleSoloFeatures(idx, feats);
        }
        if (prm.useNaradFeats) {
            addNaradowskySoloFeatures(idx, feats);
        }
        if (prm.useZhaoFeats) {
            addZhaoSoloFeatures(idx, feats);
        }
        if (prm.useBjorkelundFeats) {
            addBjorkelundSoloFeatures(idx, feats);
        }
        if (prm.useTemplates) {
            addTemplateSoloFeatures(idx, feats);
        }
        return feats;
    }
    
    public ArrayList<String> createSenseFeatureSet(int idx) {
        ArrayList<String> feats = createFeatureSet(idx);
        return feats;
    }
    
    /**
     * Creates a feature set for the given pair of word positions.
     * 
     * This defines a feature function of the form f(x, i, j), where x is a
     * vector representing all the observations about a sentence and i and j are
     * positions in the sentence.
     * 
     * Examples where this feature function would be used include factors
     * including a Role or Link variable in the SRL model, where both the parent
     * and child are tokens in the sentence.
     * 
     * @param pidx The "parent" position.
     * @param aidx The "child" position.
     * @return The features.
     */
    public ArrayList<String> createFeatureSet(int pidx, int aidx) {
        ArrayList<String> feats = new ArrayList<String>();
        if (prm.biasOnly) { return feats; }
        
        if (prm.useSimpleFeats) {
            addSimplePairFeatures(pidx, aidx, feats);
        }
        if (prm.useNaradFeats) {
            addNaradowskyPairFeatures(pidx, aidx, feats);            
        }
        if (prm.useZhaoFeats) {
            addZhaoPairFeatures(pidx, aidx, feats);
        }
        if (prm.useDepPathFeats) {
            addDependencyPathFeatures(pidx, aidx, feats);
        }
        if (prm.useBjorkelundFeats) {
            addBjorkelundPairFeatures(pidx, aidx, feats);
        }
        if (prm.useTemplates) {
            addTemplatePairFeatures(pidx, aidx, feats);
        }

        return feats;
    }

    public void addTemplateSoloFeatures(int idx, ArrayList<String> feats) {
        // TODO Auto-generated method stub
        
    }

    /* Basically the idea here is, gather every possible feature piece (POS, lemma, etc.) to place in a pair;
     * then make those pairs. We use a window of length 3 to look at the previous word, current word,
     * and next word for features, on both the first word (pred candidate) and second word (arg candidate). */
    public void addTemplatePairFeatures(int pidx, int aidx, ArrayList<String> feats) { 
        /* Features considered:
         * formFeats
         * lemmaFeats
         * tagFeats
         * morphFeats
         * deprelFeats
         * childrenFeats
         * pathFeats
         * syntacticConnectionFeats */
        // all features that are created, 
        // from combinations of features of each type.
        HashMap<Integer,ArrayList<String>> allFeats = new HashMap<Integer,ArrayList<String>>(); 
        // predicate object
        FeaturizedToken predObject;
        // argument object
        FeaturizedToken argObject;
        // pred-argument dependency path object
        FeaturizedTokenPair predArgPathObject;
        
        // feature pieces from the predicate object
        ArrayList<String> predPieces = new ArrayList<String>();
        // feature pieces from the argument object
        ArrayList<String> argPieces = new ArrayList<String>();
        // feature pieces from pred-arg path object.
        HashMap<String, HashMap<String, ArrayList<String>>> predArgPathPieces = new HashMap<String, HashMap<String, ArrayList<String>>>();
        // feature pieces from syntactic connection objects.
        ArrayList<String> predSynConnectPieces = new ArrayList<String>();
        ArrayList<String> argSynConnectPieces = new ArrayList<String>();
        // feature pieces from children objects.
        HashMap<String, HashMap<String, ArrayList<String>>> childrenPieces = new HashMap<String, HashMap<String, ArrayList<String>>>();
        // the newly constructed features to add into feats.
        // these appear in isolation, get combined together, 
        // with one another, etc.
        ArrayList<String> predFeats;
        ArrayList<String> argFeats;
        ArrayList<String> predSynConnectFeats;
        ArrayList<String> argSynConnectFeats;
        ArrayList<String> pathFeats;
        ArrayList<String> childrenFeats;
        // holder for features combining all of the above
        ArrayList<String> newFeats;

        int x = 0;
        // For pred and arg in isolation features,
        // use a window of size 3:  previous word, current word, next word.
        for (int n=-1; n<=1; n++) {
            // Get the pred and arg feature objects.
            predObject = getFeatureObject(pidx + n);
            argObject = getFeatureObject(aidx + n);
            // store the feature pieces for preds and args.
            predPieces = makePieces(predObject, n, "p");
            argPieces = makePieces(argObject, n, "a");
            // assemble the single features
            // for preds and args
            predFeats = makeFeatures(predPieces);
            argFeats = makeFeatures(argPieces);
            // add them to our list of things to combine.
            allFeats.put(x, predFeats);
            x++;
            allFeats.put(x, argFeats);
            x++;
            if (prm.syntacticConnectionFeats) {
                /*
                 * Syntactic Connection. This includes syntactic head (h), 
                 * left(right) farthest(nearest) child (lm,ln,rm,rn), 
                 * and high(low) support verb or noun. 
                 * Support verb(noun):  From the predicate or the argument 
                 * to the syntactic root along with the syntactic tree, 
                 * the first verb(noun) that is met is called as the low 
                 * support verb(noun), and the nearest one to the root is 
                 * called as the high support verb(noun).
                 * MM:  Also including sibling.
                 */
                predSynConnectPieces = makeSynConnectPieces(predObject, n);
                argSynConnectPieces = makeSynConnectPieces(argObject, n);
                // assemble the single features
                // for preds and args
                predSynConnectFeats = makeFeatures(predSynConnectPieces);
                argSynConnectFeats = makeFeatures(argSynConnectPieces);
                // add them to our list of things to combine.
                allFeats.put(x, predSynConnectFeats);
                x++;
                allFeats.put(x, argSynConnectFeats);
                x++;
            }
            // The following features are only using the current context,
            // not a sliding window around pred/arg.
            // We could do this, but Zhao et al don't seem to,
            // and it would result in a bazillion more features....
            if (n == 0) {
                if (prm.pathFeats) {
                    /* Path.
                     * From Zhao et al:
                     * There are two basic types of path between the predicate and the 
                     * argument candidates. One is the linear path (linePath) in the sequence, 
                     * the other is the path in the syntactic parsing tree (dpPath). 
                     * For the latter, we further divide it into four sub-types by considering 
                     * the syntactic root, dpPath is the full path in the syntactic tree. 
                     * Leading two paths to the root from the predicate and the argument, 
                     * respectively, the common part of these two paths will be dpPathShare. 
                     * Assume that dpPathShare starts from a node r′, then dpPathPred is 
                     * from the predicate to r′, and dpPathArg is from the argument to r′.
                     */
                    predArgPathObject = getFeatureObject(pidx, aidx);
                    // get the feature pieces for the pred-arg path features.
                    predArgPathPieces = makePathPieces(predArgPathObject);
                    // make features out of these pieces.
                    pathFeats = makeFeaturesConcat(predArgPathPieces);
                    // add them to our list of things to combine.
                    allFeats.put(x, pathFeats);
                    x++;
                }
                if (prm.childrenFeats) {
                    /*
                     * Family. Two types of children sets for the predicate or argument 
                     * candidate are considered, the first includes all syntactic children 
                     * (children), the second also includes all but excludes the left most 
                     * and the right most children (noFarChildren).
                     */
                    // get the feature pieces for the pred arg "children" features.
                    childrenPieces = makeFamilyPieces(predObject, argObject);
                    // make features out of these pieces.
                    childrenFeats = makeFeaturesConcat(childrenPieces);
                    // add them to our list of things to combine.
                    allFeats.put(x, childrenFeats);
                    x++;
                }
            }
        }
        
        // now, combine everything.
        ArrayList<String> featureList1;
        ArrayList<String> featureList2;
        Iterator<Entry<Integer, ArrayList<String>>> it1 = allFeats.entrySet().iterator();
        while (it1.hasNext()) {
            Map.Entry pairs1 = it1.next();
            int key1 = (Integer) pairs1.getKey();
            featureList1 = (ArrayList<String>) pairs1.getValue();
            newFeats = makeFeatures(featureList1);
            feats.addAll(newFeats);
            for (Entry<Integer, ArrayList<String>> entry2 : allFeats.entrySet()) {
                int key2 = entry2.getKey();
                if (key1 != key2) { 
                    featureList2 = entry2.getValue();
                    newFeats = makeFeatures(featureList1, featureList2);
                    feats.addAll(newFeats);
                }
            }
        }
    }
    
    private ArrayList<String> makeSynConnectPieces(FeaturizedToken featureObject, int n) {
        /*
         * Syntactic Connection. This includes syntactic head (h), 
         * left(right) farthest(nearest) child (lm,ln,rm,rn), 
         * and high(low) support verb or noun. 
         * Support verb(noun):  From the predicate or the argument 
         * to the syntactic root along with the syntactic tree, 
         * the first verb(noun) that is met is called as the low 
         * support verb(noun), and the nearest one to the root is 
         * called as the high support verb(noun).
         * MM:  I will also include right and left 'sibling' here.
         */
        // tmp holder for each set of constructed feature pieces
        ArrayList<String> newFeaturePieces = new ArrayList<String>();
        // holds tmp feature objects for each object (child, support word).
        int newIdx;
        FeaturizedToken newObject;
        // holds all feature pieces
        ArrayList<String> featurePieces = new ArrayList<String>();
        
        // pred high support
        newIdx = featureObject.getHighSupportVerb();
        newObject = getFeatureObject(newIdx);
        newFeaturePieces = makePieces(newObject, n, "highSupportPred");
        featurePieces.addAll(newFeaturePieces);
        // pred low support
        newIdx = featureObject.getLowSupportVerb();
        newObject = getFeatureObject(newIdx);
        newFeaturePieces = makePieces(newObject, n, "lowSupportPred");
        featurePieces.addAll(newFeaturePieces);
        // arg high support pieces
        newIdx = featureObject.getHighSupportNoun();
        newObject = getFeatureObject(newIdx);
        newFeaturePieces = makePieces(newObject, n, "highSupportArg");
        featurePieces.addAll(newFeaturePieces);
        // arg low support pieces
        newIdx = featureObject.getLowSupportNoun();
        newObject = getFeatureObject(newIdx);
        newFeaturePieces = makePieces(newObject, n, "lowSupportArg");
        featurePieces.addAll(newFeaturePieces);
        // parent pieces
        newIdx = featureObject.getParent();
        newObject = getFeatureObject(newIdx);
        newFeaturePieces = makePieces(newObject, n, "parent");
        featurePieces.addAll(newFeaturePieces);
        // far left child pieces
        newIdx = featureObject.getFarLeftChild();
        newObject = getFeatureObject(newIdx);
        newFeaturePieces = makePieces(newObject, n, "flchild");
        featurePieces.addAll(newFeaturePieces);
        // far right child pieces
        newIdx = featureObject.getFarRightChild();
        newObject = getFeatureObject(newIdx);
        newFeaturePieces = makePieces(newObject, n, "frchild");
        featurePieces.addAll(newFeaturePieces);
        // near left child pieces
        newIdx = featureObject.getNearLeftChild();
        newObject = getFeatureObject(newIdx);
        newFeaturePieces = makePieces(newObject, n, "nlchild");
        featurePieces.addAll(newFeaturePieces);
        // near right child pieces
        newIdx = featureObject.getNearRightChild();
        newObject = getFeatureObject(newIdx);
        newFeaturePieces = makePieces(newObject, n, "nrchild");
        featurePieces.addAll(newFeaturePieces);
        // left sibling pieces
        newIdx = featureObject.getNearLeftSibling();
        newObject = getFeatureObject(newIdx);
        newFeaturePieces = makePieces(newObject, n, "lsibling");
        featurePieces.addAll(newFeaturePieces);
        // right sibling pieces
        newIdx = featureObject.getNearRightSibling();
        newObject = getFeatureObject(newIdx);
        newFeaturePieces = makePieces(newObject, n, "rsibling");
        featurePieces.addAll(newFeaturePieces);

        return featurePieces;
    }

    private HashMap<String, HashMap<String, ArrayList<String>>> makeFamilyPieces(FeaturizedToken predObject, FeaturizedToken argObject) {
        // holds tmp feature objects for each group of objects (children, siblings).
        ArrayList<FeaturizedToken> newObjectList = new ArrayList<FeaturizedToken>();
        // holds tmp feature pieces for each group
        HashMap<String, HashMap<String, ArrayList<String>>> featurePieces = new HashMap<String, HashMap<String, ArrayList<String>>>();
        HashMap<String, ArrayList<String>> newFeaturePieces = new HashMap<String, ArrayList<String>>();
        
        // pred children features
        newObjectList = getFeatureObjectList(predObject.getChildren());
        newFeaturePieces = makeListPieces(newObjectList);
        featurePieces.put("p.children", newFeaturePieces);
        // pred noFarChildren features
        newObjectList = getFeatureObjectList(predObject.getNoFarChildren());
        newFeaturePieces = makeListPieces(newObjectList);
        featurePieces.put("p.noFarChildren", newFeaturePieces);
        // arg children features
        newObjectList = getFeatureObjectList(argObject.getChildren());
        newFeaturePieces = makeListPieces(newObjectList);
        featurePieces.put("a.children", newFeaturePieces);
        // arg noFarChildren features
        newObjectList = getFeatureObjectList(argObject.getNoFarChildren());
        newFeaturePieces = makeListPieces(newObjectList);
        featurePieces.put("a.noFarChildren", newFeaturePieces);

        return featurePieces;
    }
    
    /* Gathers pieces to be used in feature templates.
     * @param featureObj a pred or argument object.
     * @return featureList a list of feature pieces to be used in the templates.
     */
    private ArrayList<String> makePieces(FeaturizedToken featureObj, Integer n, String pieceType) {        
        ArrayList<String> featureList = new ArrayList<String>();
        String feat;
        String morphFeat = "";
        String prefix = "";
        if (n == -1) {
            prefix = "Prev.";
        } else if (n == 1) {
            prefix = "Next.";
        }
        prefix += pieceType + ".";
        if (prm.formFeats) {
            feat = prefix + "Form:" + featureObj.getForm();
            featureList.add(feat);
        }
        if (prm.lemmaFeats) {
            feat = prefix + "Lemma:" + featureObj.getLemma();
            featureList.add(feat);
        }
        if (prm.tagFeats) {
            feat = prefix + "Tag:" + featureObj.getPos();
            featureList.add(feat);
        }
        if (prm.morphFeats) {
            // I'm not sure of the best way to iterate over these
            // to make different combinations.
            // Here's one way.
            ArrayList<String> morphFeats = featureObj.getFeat();
            for (int i = 0; i < morphFeats.size(); i++) {
                feat = prefix + "Feat:" + morphFeats.get(i);
                featureList.add(feat);
                if (i > 0) {
                    morphFeat = morphFeat + "_" + feat;
                    featureList.add(morphFeat);
                } else {
                    morphFeat = feat;
                }
            }
        }
        if (prm.deprelFeats) {
            feat = prefix + "Deprel:" + featureObj.getDeprel();
            featureList.add(feat);
        }
        return featureList;
    }


    /* Gathers pieces to be used in feature templates.
     * @param featureObj a pred-argument path object.
     * @return featureList a list of feature pieces to be used in the templates.
     */
    private HashMap<String, HashMap<String, ArrayList<String>>> makePathPieces(FeaturizedTokenPair featureObj) {
        // holds tmp feature objects for each path type (line, dp, etc).
        ArrayList<FeaturizedToken> newObjectList = new ArrayList<FeaturizedToken>();
        // holds tmp feature pieces for each path type (line, dp, etc).
        HashMap<String, ArrayList<String>> newFeaturePieces = new HashMap<String, ArrayList<String>>();
        // holds all feature pieces
        HashMap<String, HashMap<String, ArrayList<String>>> featurePieces = new HashMap<String, HashMap<String, ArrayList<String>>>();
        
        // straight path between words
        ArrayList<Integer> linePath = featureObj.getLinePath();
        // dependency path between words
        List<Pair<Integer, Dir>> dependencyPath = featureObj.getDependencyPath();
        List<Pair<Integer, Dir>> dpPathPred = featureObj.getDpPathPred();
        List<Pair<Integer, Dir>> dpPathArg = featureObj.getDpPathArg();
        List<Pair<Integer, Dir>> dpPathShare = featureObj.getDpPathShare();
        /* For each path, create lists of FeatureObjects for all of the words in the path,
         * then make features out of them and add them to the featurePieces list.
         */
        // linePath featurepiece lists
        newObjectList = getFeatureObjectList(linePath);
        newFeaturePieces = makeListPieces(newObjectList);
        featurePieces.put("linePath", newFeaturePieces);
        // dependencyPath featurepiece lists
        newObjectList = getFeatureObjectList(dependencyPath);
        newFeaturePieces = makeListPieces(newObjectList);
        featurePieces.put("dpPath", newFeaturePieces);
        // NOTE:  does dpPathArg usually == dependencyPath?
        // dpPathPred featurepiece lists
        newObjectList = getFeatureObjectList(dpPathPred);
        newFeaturePieces = makeListPieces(newObjectList);
        featurePieces.put("dpPathPred", newFeaturePieces);
        // dpPathArg featurepiece lists
        newObjectList = getFeatureObjectList(dpPathArg);
        newFeaturePieces = makeListPieces(newObjectList);
        featurePieces.put("dpPathArg", newFeaturePieces);
        // dpPathShare featurepiece lists
        newObjectList = getFeatureObjectList(dpPathShare);
        newFeaturePieces = makeListPieces(newObjectList);
        featurePieces.put("dpPathShare", newFeaturePieces);

        return featurePieces;
    }
    
    /* Given a list of feature objects (e.g., some set of items along
     * a syntactic path), get all the possible feature pieces (POS tags, etc).
     * @return feature pieces for all objects as a list.
     */
    private HashMap<String, ArrayList<String>> makeListPieces(ArrayList<FeaturizedToken> pathList) {
        // Sets of feature pieces along the path.
        HashMap<String, ArrayList<String>> featurePieceList;
        String feat;
        String morphFeat = "";
        ArrayList<String> p;
        // For adding directionality, following Bjorkelund.
        Dir d;
        FeaturizedToken featureObj;
        // Feature pieces along all paths.
        featurePieceList = new HashMap<String, ArrayList<String>>();
        // distance feature
        String prefix = "Dist";
        feat = Integer.toString(pathList.size());
        if (!featurePieceList.containsKey(prefix)) {
            featurePieceList.put(prefix, new ArrayList<String>());
        }
        p = featurePieceList.get(prefix);
        p.add(feat);
        // not *totally* necessary.
        if (pathList.size() == 0) {
            return featurePieceList;
        }
        // NOTE:  There is probably a much faster way to do this.
        for (int x = 0; x < pathList.size(); x++) {
            featureObj = pathList.get(x);
            d = featureObj.getDirection();
            if (prm.formFeats) {
                buildPiece("Form", featureObj.getForm(), d, featurePieceList);
            }
            if (prm.lemmaFeats) {
                buildPiece("Lemma", featureObj.getLemma(), d, featurePieceList);
            }
            if (prm.tagFeats) {
                buildPiece("Pos", featureObj.getPos(), d, featurePieceList);
            }
            if (prm.morphFeats) {
                ArrayList<String> morphFeats = featureObj.getFeat();
                morphFeat = "";
                for (int i = 0; i < morphFeats.size(); i++) {
                    feat = morphFeats.get(i);
                    buildPiece("Feat", feat, d, featurePieceList);
                    if (i > 0) {
                        morphFeat += "_" + feat;
                        buildPiece("Concat.Feat", morphFeat, d, featurePieceList);
                    } else {
                        morphFeat = feat;
                    }
                }
            }
            if (prm.deprelFeats) {
                buildPiece("Deprel", featureObj.getDeprel(), d, featurePieceList);
            }
        }
        return featurePieceList;
    }    

    private void buildPiece(String prefix, String tag, Dir d, HashMap<String, ArrayList<String>> featurePieceList) {
        if (!featurePieceList.containsKey(prefix)) {
            featurePieceList.put(prefix, new ArrayList<String>());
        }
        ArrayList<String> p = featurePieceList.get(prefix);
        String feat = tag;
        p.add(feat);
        prefix += ".Dir";
        if (!featurePieceList.containsKey(prefix)) {
            featurePieceList.put(prefix, new ArrayList<String>());
        }
        p = featurePieceList.get(prefix);
        // Doesn't matter; this is just for readability.
        if (d != null) {
            feat = tag + d;
        } else {
            feat = tag;
        }
        p.add(feat);        
    }

    private ArrayList<String> makeFeaturesConcat(HashMap<String, HashMap<String, ArrayList<String>>> allPieces) {
        // Given a collected group of items for a pred or arg candidate,
        // mush them together into a feature.
        String feat;
        String prefix;
        ArrayList<String> p = new ArrayList<String>();
        ArrayList<String> featureList = new ArrayList<String>();
        for (String a : allPieces.keySet()) {
            for (Entry<String, ArrayList<String>> b : allPieces.get(a).entrySet()) {
                prefix = a + "." + b.getKey() + ".";
                p = b.getValue();
                if (p.size() > 1) {
                    feat = "Seq:" + prefix + buildString(p);
                    featureList.add(feat);
                    feat = "Bag:" + prefix + buildString(bag(p));
                    featureList.add(feat);
                    feat = "NoDup:" + prefix + buildString(noDup(p));
                    featureList.add(feat);
                } else {
                    feat = prefix + buildString(p);
                    featureList.add(feat);
                }
            }
        }
        return featureList;
    }

    private ArrayList<String> makeFeatures(ArrayList<String> featureList) {
        return featureList;
    }

    private ArrayList<String> makeFeatures(ArrayList<String> featureList1, ArrayList<String> featureList2) {
        ArrayList<String> featureList = new ArrayList<String>();
        for (String a : featureList1) {
            for (String b : featureList2) {
                String ab = a + "_" + b;
                featureList.add(ab);
            }
        }
        return featureList;
    }
    
    // ---------- Meg's "Simple" features. ---------- 
    public void addSimpleSoloFeatures(int idx, ArrayList<String> feats) {
        String wordForm = sent.getWord(idx);
        Set <String> a = sig.getSimpleUnkFeatures(wordForm, idx, cs.prm.language);
        for (String c : a) {
            feats.add(c);
        }
    }
    
    public void addSimplePairFeatures(int pidx, int aidx, ArrayList<String> feats) {
        String predForm = sent.getWord(pidx);
        String argForm = sent.getWord(aidx);
        Set <String> a = sig.getSimpleUnkFeatures(predForm, pidx, cs.prm.language);
        for (String c : a) {
            feats.add(c);
        }
        Set <String> b = sig.getSimpleUnkFeatures(argForm, aidx, cs.prm.language);
        for (String c : b) {
            feats.add(c);
        }

    }

    
    // ---------- Meg's Dependency Path features. ---------- 
    public void addDependencyPathFeatures(int pidx, int aidx, ArrayList<String> feats) {
        FeaturizedToken predObject = getFeatureObject(pidx);
        FeaturizedToken argObject = getFeatureObject(aidx);
        FeaturizedTokenPair predArgPathObject = getFeatureObject(pidx, aidx);
        List<Pair<Integer, Dir>> dependencyPath = predArgPathObject.getDependencyPath();
        String feat;
        ArrayList<String> depRelPathWord = new ArrayList<String>();
        ArrayList<FeaturizedToken> dependencyPathObjectList = getFeatureObjectList(dependencyPath);
        for (FeaturizedToken t : dependencyPathObjectList) {
            depRelPathWord.add(t.getForm());
        }
        feat = buildString(depRelPathWord);
        feats.add(feat);
        feat = buildString(bag(depRelPathWord));
        feats.add(feat);
    }    

    
    // ---------- Naradowsky et al.'s 2011 ACL features. ----------
    public void addNaradowskySenseFeatures(int idx, ArrayList<String> feats) {
        String word = sent.getWord(idx);
        String wordForm = decideForm(word, idx);
        String wordPos = sent.getPosTag(idx);
        String wordLemma = sent.getLemma(idx);

        feats.add("head_" + wordForm + "_word");
        feats.add("head_" + wordLemma + "_lemma");
        feats.add("head_" + wordPos + "_tag");
        feats.add("head_" + wordForm + "_" + wordPos + "_wordtag");
        String cap;
        if (capitalized(wordForm)) {
            cap = "UC";
        } else {
            cap = "LC";
        }
        feats.add("head_" + cap + "_caps");    }
    
    public void addNaradowskySoloFeatures(int idx, ArrayList<String> feats) {
        addNaradowskySenseFeatures(idx, feats);
    }

    /** Made up by Meg based on pair and sense features. 
    public void addNaradowskySoloFeatures(int idx, ArrayList<String> feats) {
        addNaradowskySenseFeatures(idx, feats);
        String word = sent.getWord(idx);
        String wordForm = decideForm(word, idx);
        String wordPos = sent.getPosTag(idx);

        feats.add("head_" + wordForm + "_dep_" + wordPos + "_wordpos");
        feats.add("slen_" + sent.size());    
        feats.add("head_" + wordForm + "_word");
        feats.add("head_" + wordPos + "_tag");
    } **/
    
    
    public void addNaradowskyPairFeatures(int pidx, int aidx, ArrayList<String> feats) {
        String predWord = sent.getWord(pidx);
        String argWord = sent.getWord(aidx);
        String predForm = decideForm(predWord, pidx);
        String argForm = decideForm(argWord, aidx);
        String predPos = sent.getPosTag(pidx);
        String argPos = sent.getPosTag(aidx);
        String dir;
        int dist = Math.abs(aidx - pidx);
        if (aidx > pidx) 
            dir = "RIGHT";
        else if (aidx < pidx) 
            dir = "LEFT";
        else 
            dir = "SAME";
    
        feats.add("head_" + predForm + "dep_" + argForm + "_word");
        feats.add("head_" + predPos + "_dep_" + argPos + "_pos");
        feats.add("head_" + predForm + "_dep_" + argPos + "_wordpos");
        feats.add("head_" + predPos + "_dep_" + argForm + "_posword");
        feats.add("head_" + predForm + "_dep_" + argForm + "_head_" + predPos + "_dep_" + argPos + "_wordwordpospos");
    
        feats.add("head_" + predPos + "_dep_" + argPos + "_dist_" + dist + "_posdist");
        feats.add("head_" + predPos + "_dep_" + argPos + "_dir_" + dir + "_posdir");
        feats.add("head_" + predPos + "_dist_" + dist + "_dir_" + dir + "_posdistdir");
        feats.add("head_" + argPos + "_dist_" + dist + "_dir_" + dir + "_posdistdir");
    
        feats.add("slen_" + sent.size());
        feats.add("dir_" + dir);
        feats.add("dist_" + dist);
        feats.add("dir_dist_" + dir + dist);
    
        feats.add("head_" + predForm + "_word");
        feats.add("head_" + predPos + "_tag");
        feats.add("arg_" + argForm + "_word");
        feats.add("arg_" + argPos + "_tag");
        
        if (prm.withSupervision) {
            List<String> predFeats = sent.getFeats(pidx);
            List<String> argFeats = sent.getFeats(aidx);
            if (predFeats == null) {
                predFeats = new ArrayList<String>();
                predFeats.add("_");
            }
            if (argFeats == null) {
                argFeats = new ArrayList<String>();
                argFeats.add("_");
            }
            for (String m1 : predFeats) {
                for (String m2 : argFeats) {
                    feats.add(m1 + "_" + m2 + "_morph");
                }
            }
        }
    }
    
    
    // ---------- Zhao et al's 2009 CoNLL features. ----------     
    public void addZhaoSenseFeatures(int idx, ArrayList<String> feats) {
        /* This is the feature set for ENGLISH sense classification given
         * by Zhao et al 2009.  Spanish sense classification is not given. */
        FeaturizedToken p = getFeatureObject(idx);
        FeaturizedToken p1 = getFeatureObject(idx + 1);
        FeaturizedToken pm1 = getFeatureObject(idx - 1);

        addZhaoUnsupervisedSenseFeats(p, p1, pm1, feats);
        if (prm.withSupervision) {
            addZhaoSupervisedSenseFeats(p, p1, feats);
        }
    }
    
    private void addZhaoUnsupervisedSenseFeats(FeaturizedToken p, FeaturizedToken p1, FeaturizedToken pm1, ArrayList<String> feats) {
        String feat;
        // p.form
        feat = p.getForm();
        feats.add(feat);
        // p.form−1 + p.form 
        feat = pm1.getForm() + p.getForm();
        feats.add(feat);
        // p.form + p.form1
        feat = p.getForm() + p1.getForm();
        feats.add(feat); 
    }
    
    private void addZhaoSupervisedSenseFeats(FeaturizedToken p, FeaturizedToken p1, ArrayList<String> feats) {
        String feat;
        // p.lm.pos
        feat = getFeatureObject(p.getFarLeftChild()).getPos();
        feats.add(feat);
        // p.rm.pos
        feat = getFeatureObject(p.getFarRightChild()).getPos();
        feats.add(feat);
        // p.lemma
        feat = p.getLemma();
        feats.add(feat);
        // p.lemma + p.lemma1
        feat = p.getLemma() + p1.getLemma();
        feats.add(feat);
        // p.lemma + p.children.dprel.noDup 
        ArrayList<String> depRelChildren = new ArrayList<String>();
        ArrayList<FeaturizedToken> pChildren = getFeatureObjectList(p.getChildren());
        for (FeaturizedToken child : pChildren) {
            depRelChildren.add(child.getDeprel());
        }
        feat = buildString(noDup(depRelChildren));
        feats.add(feat);
        // Er...what?  Sense given for sense prediction?
        // p.lemma + p.currentSense
    }
    
    public void addZhaoSoloFeatures(int idx, ArrayList<String> feats) {
        // Zhao doesn't have 'solo' features for SRL, just sense.
        addZhaoSenseFeatures(idx, feats);
    }   
    
    public void addZhaoPairFeatures(int pidx, int aidx, ArrayList<String> feats) {
        /* NOTE:  Not sure about they're "Semantic Connection" features.  
         * What do they correspond to in CoNLL data? 
         * From paper: "This includes semantic head (semhead), left(right) farthest(nearest) 
         * semantic child (semlm, semln, semrm, semrn). 
         * We say a predicate is its argument's semantic head, and the latter is the former's child. 
         * Features related to this type may track the current semantic parsing status." 
         * If a semantic predicate is given, then the SRL task is moot... */

        FeaturizedToken pred = getFeatureObject(pidx);
        FeaturizedToken arg = getFeatureObject(aidx);
        FeaturizedTokenPair predArgPair = getFeatureObject(pidx, aidx);
        FeaturizedToken predLast = getFeatureObject(pidx - 1);
        FeaturizedToken predNext = getFeatureObject(pidx + 1);
        //FeatureObject predParent = getFeatureObject(pred.getParent());
        FeaturizedToken argLast = getFeatureObject(aidx - 1);
        FeaturizedToken argNext = getFeatureObject(aidx + 1);
        FeaturizedToken argParent = getFeatureObject(arg.getParent());
                
        ArrayList<Integer> predChildren = pred.getChildren();
        ArrayList<Integer> argChildren = arg.getChildren();
        List<Pair<Integer, Dir>> dependencyPath = predArgPair.getDependencyPath();
        
        // Initialize Path structures.
        //List<Pair<Integer, Dir>> dpPathPred = predArgPair.getDpPathPred();
        List<Pair<Integer, Dir>> dpPathArg = predArgPair.getDpPathArg();
        ArrayList<Integer> linePath = predArgPair.getLinePath();

        ArrayList<FeaturizedToken> predChildrenObjectList = getFeatureObjectList(predChildren);
        ArrayList<FeaturizedToken> argChildrenObjectList = getFeatureObjectList(argChildren);
        ArrayList<FeaturizedToken> dependencyPathObjectList = getFeatureObjectList(dependencyPath);
        ArrayList<FeaturizedToken> linePathCoNLL = getFeatureObjectList(linePath);
        
        // Add the supervised features
        if (prm.withSupervision) {
            addZhaoSupervisedPredFeats(pred, arg, predLast, predNext, predChildrenObjectList, feats);
            addZhaoSupervisedArgFeats(pred, arg, predLast, predNext, argLast, argNext, argParent, argChildrenObjectList, feats);
            addZhaoSupervisedCombinedFeats(pred, arg, feats, dependencyPathObjectList, linePathCoNLL, dpPathArg); 
        }
        
        // Add the unsupervised features
        addZhaoUnsupervisedPredFeats(pred, predLast, predNext, feats);
        addZhaoUnsupervisedArgFeats(arg, argLast, argNext, argChildrenObjectList, feats);
        addZhaoUnsupervisedCombinedFeats(linePath, linePathCoNLL, feats);
    }
    
    private void addZhaoUnsupervisedCombinedFeats(ArrayList<Integer> linePath, ArrayList<FeaturizedToken> linePathCoNLL, ArrayList<String> feats) {
        // ------- Combined features (unsupervised) ------- 
        String feat;
        // a:p|linePath.distance 
        feat = Integer.toString(linePath.size());
        feats.add(feat);
        // a:p|linePath.form.seq 
        ArrayList<String> linePathForm = new ArrayList<String>();
        for (FeaturizedToken t : linePathCoNLL) {
            linePathForm.add(t.getForm());
        }
        feat = buildString(linePathForm);
        feats.add(feat);
    }

    private void addZhaoUnsupervisedArgFeats(FeaturizedToken arg, FeaturizedToken argNext, FeaturizedToken argLast,
            ArrayList<FeaturizedToken> argChildrenObjectList, ArrayList<String> feats) {
        // ------- Argument features (unsupervised) ------- 
        String feat;
        // a.lm.form
        feat = getFeatureObject(arg.getFarLeftChild()).getForm();
        feats.add(feat);
        // a_1.form
        feat = argLast.getForm();
        feats.add(feat);
        // a.form + a1.form
        feat = arg.getForm() + "_" + argNext.getForm();
        feats.add(feat);
        // a.form + a.children.pos 
        ArrayList<String> argChildrenPos = new ArrayList<String>();
        for (FeaturizedToken child : argChildrenObjectList) {
            argChildrenPos.add(child.getPos());
        }
        feat = arg.getForm()  + "_" + buildString(argChildrenPos);
        feats.add(feat);
        // a1.pos + a.pos.seq
        feat = argNext.getPos() + "_" + arg.getPos();
        feats.add(feat);
    }

    private void addZhaoUnsupervisedPredFeats(FeaturizedToken pred, FeaturizedToken predLast, FeaturizedToken predNext, ArrayList<String> feats) {
        // ------- Predicate features (unsupervised) ------- 
        String feat12;
        // p.pos_1 + p.pos
        feat12 = predLast.getPos() + "_" + pred.getPos();
        feats.add(feat12);
        String feat13;
        // p.pos1
        feat13 = predNext.getPos();
        feats.add(feat13);
    }
    
    private void addZhaoSupervisedCombinedFeats(FeaturizedToken pred, FeaturizedToken arg, ArrayList<String> feats, ArrayList<FeaturizedToken> dependencyPathObjectList,
            ArrayList<FeaturizedToken> linePathCoNLL, List<Pair<Integer, Dir>> dpPathArg) {
        // ------- Combined features (supervised) -------
        String feat;
        // a.lemma + p.lemma 
        feat = arg.getLemma() + "_" + pred.getLemma();
        feats.add(feat);
        // (a:p|dpPath.dprel) + p.FEAT1 
        // a:p|dpPath.lemma.seq 
        // a:p|dpPath.lemma.bag 
        ArrayList<String> depRelPath = new ArrayList<String>();
        ArrayList<String> depRelPathLemma = new ArrayList<String>();
        for (FeaturizedToken t : dependencyPathObjectList) {
            depRelPath.add(t.getDeprel());
            depRelPathLemma.add(t.getLemma());
        }

        feat = buildString(depRelPath) + pred.getFeat().get(0);        
        feats.add(feat);
        feat = buildString(depRelPathLemma);
        feats.add(feat);
        ArrayList<String> depRelPathLemmaBag = bag(depRelPathLemma);
        feat = buildString(depRelPathLemmaBag);
        feats.add(feat);
        // a:p|linePath.FEAT1.bag 
        // a:p|linePath.lemma.seq 
        // a:p|linePath.dprel.seq 
        ArrayList<String> linePathFeat = new ArrayList<String>();
        ArrayList<String> linePathLemma = new ArrayList<String>();
        ArrayList<String> linePathDeprel = new ArrayList<String>();
        for (FeaturizedToken t : linePathCoNLL) {
            linePathFeat.add(t.getFeat().get(0));
            linePathLemma.add(t.getLemma());
            linePathDeprel.add(t.getDeprel());
        }
        ArrayList<String> linePathFeatBag = bag(linePathFeat);
        feat = buildString(linePathFeatBag);
        feats.add(feat);
        feat = buildString(linePathLemma);
        feats.add(feat);
        feat = buildString(linePathDeprel);
        feats.add(feat);
        ArrayList<String> dpPathLemma = new ArrayList<String>();
        for (Pair<Integer, Dir> dpP : dpPathArg) {
            dpPathLemma.add(getFeatureObject(dpP.get1()).getLemma());            
        }
        // a:p|dpPathArgu.lemma.seq 
        feat = buildString(dpPathLemma);
        feats.add(feat);
        // a:p|dpPathArgu.lemma.bag
        feat = buildString(bag(dpPathLemma));
        feats.add(feat);
    }
    
    private void addZhaoSupervisedArgFeats(FeaturizedToken pred, FeaturizedToken arg, FeaturizedToken predLast,
            FeaturizedToken predNext, FeaturizedToken argLast, FeaturizedToken argNext, FeaturizedToken argParent, ArrayList<FeaturizedToken> argChildrenObjectList, ArrayList<String> feats) {
        // ------- Argument features (supervised) ------- 
        getFirstThirdSupArgFeats(pred, arg, predLast, predNext, argLast, argNext, argParent, argChildrenObjectList, feats);
        getSecondThirdSupArgFeats(pred, arg, predLast, predNext, argLast, argNext, argParent, argChildrenObjectList, feats);
        getThirdThirdSupArgFeats(pred, arg, predLast, predNext, argLast, argNext, argParent, argChildrenObjectList, feats);
    }

    private void getFirstThirdSupArgFeats(FeaturizedToken pred, FeaturizedToken arg, FeaturizedToken predLast,
            FeaturizedToken predNext, FeaturizedToken argLast, FeaturizedToken argNext, FeaturizedToken argParent, 
            ArrayList<FeaturizedToken> argChildrenObjectList, ArrayList<String> feats) {
        String feat;
        List<String> argFeats = arg.getFeat();
        String a1; String a2; String a3; String a4; String a5; String a6;
        a1 = argFeats.get(0);
        a2 = argFeats.get(1);
        a3 = argFeats.get(2);
        a4 = argFeats.get(3);
        a5 = argFeats.get(4);
        a6 = argFeats.get(5);
        List<String> argLastFeats = argLast.getFeat();
        //String last_a1 = argLastFeats.get(0);
        String last_a2 = argLastFeats.get(1);
        List<String> argNextFeats = argNext.getFeat();
        //String next_a1 = argNextFeats.get(0);
        //String next_a2 = argNextFeats.get(1);
        String next_a3 = argNextFeats.get(2);
        // a.FEAT1 + a.FEAT3 + a.FEAT4 + a.FEAT5 + a.FEAT6 
        feat = a1 + "_" + a3 + "_" + a4 + "_" + a5 + "_" + a6;
        feats.add(feat);
        // a_1.FEAT2 + a.FEAT2 
        feat = last_a2 + "_" + a2;
        feats.add(feat);
        // a.FEAT3 + a1.FEAT3
        feat = a3 + "_" + next_a3;
        feats.add(feat);
        // a.FEAT3 + a.h.FEAT3 
        feat = argParent.getFeat().get(2);
        feats.add(feat);
        // a.children.FEAT1.noDup 
        ArrayList<String> argChildrenFeat1 = getChildrenFeat1(argChildrenObjectList);
        ArrayList<String> argChildrenFeat1NoDup = noDup(argChildrenFeat1);
        feat = buildString(argChildrenFeat1NoDup);
        feats.add(feat);
        // a.children.FEAT3.bag 
        ArrayList<String> argChildrenFeat3 = getChildrenFeat3(argChildrenObjectList);
        ArrayList<String> argChildrenFeat3Bag = bag(argChildrenFeat3);
        feat = buildString(argChildrenFeat3Bag);
        feats.add(feat);
    }
    
    private void getSecondThirdSupArgFeats(FeaturizedToken pred, FeaturizedToken arg, FeaturizedToken predLast,
            FeaturizedToken predNext, FeaturizedToken argLast, FeaturizedToken argNext, FeaturizedToken argParent, 
            ArrayList<FeaturizedToken> argChildrenObjectList, ArrayList<String> feats) {
        FeaturizedToken argLm = getFeatureObject(arg.getFarLeftChild());
        FeaturizedToken argRm = getFeatureObject(arg.getFarRightChild());
        FeaturizedToken argRn = getFeatureObject(arg.getNearRightChild());
        //FeatureObject argLn = getFeatureObject(arg.getNearLeftChild());
        String feat;
        // a.h.lemma
        feats.add(argParent.getLemma());
        // a.lm.dprel + a.form
        feats.add(argLm.getDeprel() + "_" + arg.getForm());
        // a.lm_1.lemma
        feats.add(getFeatureObject(argLast.getFarLeftChild()).getLemma());
        // a.lmn.pos (n=0,1) 
        feats.add(argLm.getPos());
        feats.add(getFeatureObject(argNext.getFarLeftChild()).getPos());
        // a.noFarChildren.pos.bag + a.rm.form 
        ArrayList<Integer> noFarChildren = arg.getNoFarChildren();
        ArrayList<String> noFarChildrenPos = new ArrayList<String>();
        for (Integer i : noFarChildren) {
            noFarChildrenPos.add(getFeatureObject(i).getPos()); 
        }
        ArrayList<String> argNoFarChildrenBag = bag(noFarChildrenPos);
        feat = buildString(argNoFarChildrenBag) + argRm.getForm();
        feats.add(feat);
        // a.pphead.lemma
        feats.add(getFeatureObject(arg.getParent()).getLemma());
        // a.rm.dprel + a.form
        feats.add(argRm.getDeprel() + "_" + arg.getForm());
        // a.rm_1.form 
        feats.add(getFeatureObject(argLast.getFarRightChild()).getForm());
        // a.rm.lemma
        feats.add(argRm.getLemma());
        // a.rn.dprel + a.form 
        feats.add(argRn.getDeprel() + "_" + arg.getForm());        
    }
    
    private void getThirdThirdSupArgFeats(FeaturizedToken pred, FeaturizedToken arg, FeaturizedToken predLast,
            FeaturizedToken predNext, FeaturizedToken argLast, FeaturizedToken argNext, FeaturizedToken argParent, 
            ArrayList<FeaturizedToken> argChildrenObjectList, ArrayList<String> feats) {
        String feat;
        // a.lowSupportVerb.lemma 
        feats.add(getFeatureObject(arg.getLowSupportNoun()).getLemma());
        // a.lemma + a.h.form 
        feats.add(arg.getLemma() + "_" + argParent.getForm());
        // a.lemma + a.pphead.form 
        feats.add(arg.getLemma() + "_" + getFeatureObject(arg.getParent()).getForm());
        // a1.lemma
        feats.add(argNext.getLemma());
        // a.pos + a.children.dprel.bag
        ArrayList<String> argChildrenDeprel = new ArrayList<String>(); 
        for (FeaturizedToken child : argChildrenObjectList) {
            argChildrenDeprel.add(child.getDeprel());
        }
        ArrayList<String> argChildrenDeprelBag = bag(argChildrenDeprel);
        feat = arg.getPos() + buildString(argChildrenDeprelBag);
        feats.add(feat);
    }

    private void addZhaoSupervisedPredFeats(FeaturizedToken pred, FeaturizedToken arg, FeaturizedToken predLast, FeaturizedToken predNext, ArrayList<FeaturizedToken> predChildrenObjectList, ArrayList<String> feats) {
        // ------- Predicate features (supervised) ------- 
        
        // NOTE: We cannot include these features in our model since they would have
        // to use the gold predicate sense.
        // 
        //        // p.currentSense + p.lemma 
        //        feats.add(pred.getSense() + "_" + pred.getLemma());
        //        // p.currentSense + p.pos 
        //        feats.add(pred.getSense() + "_" + pred.getPos());
        //        // p.currentSense + a.pos 
        //        feats.add(pred.getSense() + "_" + arg.getPos());
        
        // p_1.FEAT1
        feats.add(predLast.getFeat().get(0));
        // p.FEAT2
        feats.add(pred.getFeat().get(1));
        // p1.FEAT3
        feats.add(predNext.getFeat().get(2));
        // NOTE:  This is supposed to be p.semrm.semdprel  What is this?  
        // I'm not sure.  Here's just a guess.
        feats.add(getFeatureObject(pred.getFarRightChild()).getDeprel());
        // p.lm.dprel        
        feats.add(getFeatureObject(pred.getFarLeftChild()).getDeprel());
        // p.form + p.children.dprel.bag 
        ArrayList<String> predChildrenDeprel = new ArrayList<String>();
        for (FeaturizedToken child : predChildrenObjectList) {
            predChildrenDeprel.add(child.getDeprel());
        }
        String bagDepPredChildren = buildString(bag(predChildrenDeprel));
        feats.add(pred.getForm() + "_" + bagDepPredChildren);
        // p.lemma_n (n = -1, 0) 
        feats.add(predLast.getLemma());
        feats.add(pred.getLemma());
        // p.lemma + p.lemma1
        feats.add(pred.getLemma() + "_" + predNext.getLemma());
        // p.pos + p.children.dprel.bag 
        feats.add(pred.getPos() + "_" + bagDepPredChildren);
    }

    
    // ---------- Bjorkelund et al, CoNLL2009 features. ---------- 
    public void addBjorkelundSenseFeatures(int idx, ArrayList<String> feats) {
        FeaturizedToken pred = getFeatureObject(idx);
        FeaturizedToken predParent = getFeatureObject(pred.getParent());
        ArrayList<Integer> predChildren = pred.getChildren();
        // PredWord, PredPOS, PredDeprel, PredFeats
        addBjorkelundGenericFeatures(idx, feats, "Pred");
        // predParentWord, predParentPOS, predParentFeats
        addBjorkelundPredParentFeatures(predParent, feats);
        // ChildDepSet, ChildWordSet, ChildPOSSet
        addBjorkelundPredChildFeatures(predChildren, feats);
        // TODO: DepSubCat: the subcategorization frame of the predicate, e.g. OBJ+OPRD+SUB.
    }
    
    public void addBjorkelundSoloFeatures(int idx, ArrayList<String> feats) {
        /* No 'solo' features, per se, just Sense features for a solo predicate.
         * But, some features are the same type for predicate and argument. 
         * These are defined in add BjorkelundGenericFeatures. */
        addBjorkelundSenseFeatures(idx, feats);
    }
    
    public void addBjorkelundPairFeatures(int pidx, int aidx, ArrayList<String> feats) {
        /* From Bjorkelund et al, CoNLL 2009.
         * Prefixes: 
         * Pred:  the predicate
         * PredParent:  the parent of the predicate
         * Arg:  the argument
         * Left:  the leftmost dependent of the argument
         * Right:   the rightmost dependent of the argument
         * LeftSibling:  the left sibling of the argument
         * RightSibling:  the right sibling of the argument
         */        
        String feat;
        FeaturizedToken pred = getFeatureObject(pidx);
        FeaturizedToken arg = getFeatureObject(aidx);
        FeaturizedTokenPair predArgPair = getFeatureObject(pidx, aidx);
        FeaturizedToken predParent = getFeatureObject(pred.getParent());
        ArrayList<Integer> predChildren = pred.getChildren();
        FeaturizedToken argLeftSibling = getFeatureObject(arg.getNearLeftSibling());
        FeaturizedToken argRightSibling = getFeatureObject(arg.getNearRightSibling());
        FeaturizedToken argLeftDependent = getFeatureObject(arg.getFarLeftChild());
        FeaturizedToken argRightDependent = getFeatureObject(arg.getFarRightChild());
        
        // PredWord, PredPOS, PredFeats, PredDeprel
        addBjorkelundGenericFeatures(pidx, feats, "Pred");
        // ArgWord, ArgPOS, ArgFeats, ArgDeprel
        addBjorkelundGenericFeatures(aidx, feats, "Arg");
        // PredParentWord, PredParentPOS, PredParentFeats
        addBjorkelundPredParentFeatures(predParent, feats);
        // ChildDepSet, ChildWordSet, ChildPOSSet
        addBjorkelundPredChildFeatures(predChildren, feats);
        // LeftWord, LeftPOS, LeftFeats
        addBjorkelundDependentFeats(argLeftDependent, feats, "Left");
        // RightWord, RightPOS, RightFeats
        addBjorkelundDependentFeats(argRightDependent, feats, "Right");
        // LeftSiblingWord, LeftSiblingPOS, LeftSiblingFeats
        addBjorkelundSiblingFeats(argLeftSibling, feats, "Left");
        // RightSiblingWord, RightSiblingPOS, RightSiblingFeats
        addBjorkelundSiblingFeats(argRightSibling, feats, "Right");
        // DeprelPath, POSPath
        addBjorkelundPathFeats(predArgPair, feats);
        // Position
        addBjorkelundPositionFeat(pidx, aidx, feats);
        
        // PredLemma
        feat = pred.getLemma();
        feats.add("PredLemma:" + feat);
        // TODO: Sense: the value of the Pred column, e.g. plan.01.
        // TODO: DepSubCat: the subcategorization frame of the predicate, e.g. OBJ+OPRD+SUB.
    }
    
    private void addBjorkelundGenericFeatures(int idx, ArrayList<String> feats, String type) {
        String feat;
        FeaturizedToken bjorkWord = getFeatureObject(idx);
        // ArgWord, PredWord
        feat = bjorkWord.getForm();
        feats.add(type + ":" + feat);
        // ArgPOS, PredPOS,
        feat = bjorkWord.getPos();
        feats.add(type + ":" + feat);
        // ArgFeats, PredFeats
        feat = buildString(bjorkWord.getFeat());
        feats.add(type + ":" + feat);
        // ArgDeprel, PredDeprel
        feat = bjorkWord.getDeprel();
        feats.add(type + ":" + feat);
    }

    private void addBjorkelundPositionFeat(int pidx, int aidx, ArrayList<String> feats) {
        String feat;
        // Position: the position of the argument with respect to the predicate, i.e. before, on, or after.
        if (pidx < aidx) {
            feat = "before";
        } else if (pidx > aidx) {
            feat = "after";
        } else {
            feat = "on";
        }
        feats.add("Position:" + feat);        
    }

    private void addBjorkelundPathFeats(FeaturizedTokenPair predArgPair, ArrayList<String> feats) {
        String feat;
        List<Pair<Integer, Dir>> dependencyPath = predArgPair.getDependencyPath();
        // DeprelPath: the path from predicate to argument concatenating dependency labels with the
        // direction of the edge, e.g. OBJ↑OPRD↓SUB↓.
        ArrayList<String> depRelPath = new ArrayList<String>();
        // POSPath: same as DeprelPath, but dependency labels are exchanged for POS tags, e.g. NN↑NNS↓NNP↓.
        ArrayList<String> posPath = new ArrayList<String>();
        ArrayList<FeaturizedToken> dependencyPathObjectList = getFeatureObjectList(dependencyPath);
        for (int i = 0; i < dependencyPathObjectList.size(); i++) {
            FeaturizedToken t = dependencyPathObjectList.get(i);
            depRelPath.add(t.getDeprel() + ":" + dependencyPath.get(i).get2());
            posPath.add(t.getPos() + ":" + dependencyPath.get(i).get2());
        }
        feat = buildString(depRelPath);
        feats.add("DeprelPath:" + feat);
        feat = buildString(posPath);
        feats.add("PosPath:" + feat);
    }

    private void addBjorkelundSiblingFeats(FeaturizedToken argSibling, ArrayList<String> feats, String dir) {
        String feat;
        // LeftSiblingWord, RightSiblingWord
        feat = argSibling.getForm();
        feats.add(dir + "SiblingWord:" + feat);
        // LeftSiblingPOS, RightSiblingPOS
        feat = argSibling.getPos();
        feats.add(dir + "SiblingPos:" + feat);
        // LeftSiblingFeats, RightSiblingFeats
        feat = buildString(argSibling.getFeat());
        feats.add(dir + "SiblingFeats:" + feat);
    }

    private void addBjorkelundDependentFeats(FeaturizedToken dependent, ArrayList<String> feats, String dir) {
        String feat;
        // LeftWord, RightWord
        feat = dependent.getForm();
        feats.add(dir + "Word:" + feat);
        // LeftPOS, RightPOS
        feat = dependent.getPos();
        feats.add(dir + "POS:" + feat);
        // LeftFeats, RightFeats
        feat = buildString(dependent.getFeat());
        feats.add(dir + "Feats:" + feat);
    }

    private void addBjorkelundPredChildFeatures(ArrayList<Integer> predChildren, ArrayList<String> feats) {
        String feat;
        // ChildDepSet: the set of dependency labels of the children of the predicate, e.g. {OBJ, SUB}.
        ArrayList<String> childDepSet = new ArrayList<String>();
        // ChildPOSSet: the set of POS tags of the children of the predicate, e.g. {NN, NNS}.
        ArrayList<String> childPosSet = new ArrayList<String>();
        // ChildWordSet: the set of words (Form) of the children of the predicate, e.g. {fish, me}.
        ArrayList<String> childWordSet = new ArrayList<String>();
        for (int child : predChildren) {
            childDepSet.add(getFeatureObject(child).getDeprel());
            childPosSet.add(getFeatureObject(child).getPos());
            childWordSet.add(getFeatureObject(child).getForm());
        }
        feat = buildString(childDepSet);
        feats.add("ChildDepSet:" + feat);
        feat = buildString(childPosSet);
        feats.add("ChildPOSSet:" + feat);
        feat = buildString(childWordSet);
        feats.add("ChildWordSet:" + feat);        
    }

    private void addBjorkelundPredParentFeatures(FeaturizedToken predParent, ArrayList<String> feats) {
        String feat;
        // PredParentWord
        feat = predParent.getForm();
        feats.add("PredParentWord:" + feat);
        // PredParentPOS
        feat = predParent.getPos();
        feats.add("PredParentPos:" + feat);
        // PredParentFeats
        feat = buildString(predParent.getFeat());
        feats.add("PredParentFeats:" + feat);        
    }

    

    // ---------- Helper functions ---------- 

    private String buildString(ArrayList<String> input) {
        StringBuilder buffer = new StringBuilder(50);
        int j = input.size();
        if (j > 0) {
            buffer.append(input.get(0));
            for (int i = 1; i < j; i++) {
                buffer.append("_").append(input.get(i));
            }
        }
        return buffer.toString();
    }
    
    private ArrayList<String> getChildrenFeat3(ArrayList<FeaturizedToken> childrenObjectList) {
        ArrayList<String> childrenFeat3 = new ArrayList<String>();
        for (FeaturizedToken child : childrenObjectList) {
            childrenFeat3.add(child.getFeat().get(2));
        }
        return childrenFeat3;
    }

    private ArrayList<String> getChildrenFeat1(ArrayList<FeaturizedToken> childrenObjectList) {
        ArrayList<String> childrenFeat1 = new ArrayList<String>();
        for (FeaturizedToken child : childrenObjectList) {
            childrenFeat1.add(child.getFeat().get(0));
        }
        return childrenFeat1;
    }

    private static ArrayList<String> bag(ArrayList<String> elements) {
        // bag, which removes all duplicated strings and sort the rest
        return (ArrayList<String>) asSortedList(new HashSet<String>(elements));
    }
    
    private static ArrayList<String> noDup(ArrayList<String> argChildrenFeat1) {
        // noDup, which removes all duplicated neighbored strings.
        ArrayList<String> noDupElements = new ArrayList<String>();
        String lastA = null;
        for (String a : argChildrenFeat1) {
            if (!a.equals(lastA)) {
                noDupElements.add(a);
            }
            lastA = a;
        }
        return noDupElements;
    }
    
    private FeaturizedToken getFeatureObject(int idx) {
        return fSent.getFeatTok(idx);
    }

    private FeaturizedTokenPair getFeatureObject(int pidx, int cidx) {
        return fSent.getFeatTokPair(pidx, cidx);
    }
    
    private boolean capitalized(String wordForm) {
        if (wordForm.length() == 0) {
            return true;
        }
        char ch = wordForm.charAt(0);
        if (Character.isUpperCase(ch)) {
            return true;
        }
        return false;
    }

    private ArrayList<FeaturizedToken> getFeatureObjectList(List<Pair<Integer, Dir>> path) {
        ArrayList<FeaturizedToken> pathObjectList = new ArrayList<FeaturizedToken>();
        for (Pair<Integer,Dir> p : path) {
            FeaturizedToken newFeatureObject = getFeatureObject(p.get1());
            // Adding directionality here, given the type of path.
            // These serve as additional features following Bjorkelund.
            newFeatureObject.setDirection(p.get2());
            pathObjectList.add(newFeatureObject);
        }
        return pathObjectList;
    }

    private ArrayList<FeaturizedToken> getFeatureObjectList(ArrayList<Integer> children) {
        ArrayList<FeaturizedToken> featureObjectList = new ArrayList<FeaturizedToken>();
        for (int child : children) {
            featureObjectList.add(getFeatureObject(child));
        }
        return featureObjectList;
    }
    
    private static <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
      List<T> list = new ArrayList<T>(c);
      Collections.sort(list);
      return list;
    }
    
    private String decideForm(String wordForm, int idx) {
        String cleanWord = cs.normalize.clean(wordForm);

        if (!cs.knownWords.contains(cleanWord)) {
            String unkWord = cs.sig.getSignature(wordForm, idx, cs.prm.language);
            unkWord = cs.normalize.escape(unkWord);
            if (!cs.knownUnks.contains(unkWord)) {
                unkWord = "UNK";
                return unkWord;
            }
        }
        
        return cleanWord;
    }
    
}
