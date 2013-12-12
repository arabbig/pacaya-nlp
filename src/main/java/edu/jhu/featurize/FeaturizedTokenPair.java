package edu.jhu.featurize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.jhu.data.DepTree;
import edu.jhu.data.DepTree.Dir;
import edu.jhu.data.concrete.SimpleAnnoSentence;
import edu.jhu.data.conll.SrlGraph.SrlPred;
import edu.jhu.prim.tuple.Pair;

/**
 * Cache of features for a pair of words in a sentence (parent/predicate and
 * child/argument).
 * 
 * @author mmitchell
 */
public class FeaturizedTokenPair {
    
    /* Feature constructor based on CoNLL 2009:
     * "Multilingual Dependency Learning:
     * A Huge Feature Engineering Method to Semantic Dependency Parsing"
     * Hai Zhao, Wenliang Chen, Chunyu Kit, Guodong Zhou 
     * and
     * "Multilingual Semantic Role Labeling"
     * Anders Bjo ̈rkelund, Love Hafdell, Pierre Nugues
     * Treats features as combinations of feature templates, for:
     * 1. word form (formFeats)
     * 2. lemma (lemmaFeats)
     * 3. part-of-speech (tagFeats)
     * 4. morphological features (morphFeats)
     * 5. syntactic dependency label (deprelFeats)
     * 6. children (childrenFeats)
     * 7. dependency paths (pathFeats)
     * 8. 'high' and 'low' support, siblings, parents (syntacticConnectionFeats).
     */    

    private int[] parents;
    private ArrayList<Integer> linePath;
    private List<Pair<Integer, Dir>> dependencyPath;
    private ArrayList<Pair<Integer, Dir>> dpPathShare;
    private List<Pair<Integer, Dir>> dpPathPred;
    private List<Pair<Integer, Dir>> dpPathArg;
    
    public FeaturizedTokenPair(int pidx, int aidx, FeaturizedToken zhaoPred, FeaturizedToken zhaoArg, int[] parents) {
        this.parents = parents;
        this.linePath = new ArrayList<Integer>();
        this.dpPathShare = new ArrayList<Pair<Integer,DepTree.Dir>>();
        /* ZHAO:  Path. There are two basic types of path between the predicate and the argument candidates. 
         * One is the linear path (linePath) in the sequence, the other is the path in the syntactic 
         * parsing tree (dpPath). For the latter, we further divide it into four sub-types by 
         * considering the syntactic root, dpPath is the full path in the syntactic tree. */
        cacheDependencyPath(pidx, aidx);
        cacheLinePath(pidx, aidx);
        cacheDpPathShare(pidx, aidx, zhaoPred, zhaoArg);
    }    
    
    // ------------------------ Getters and Caching Methods ------------------------ //
        
    public List<Pair<Integer,Dir>> getDependencyPath() {
        return dependencyPath;
    }
    
    private void cacheDependencyPath(int pidx, int aidx) {
        this.dependencyPath = DepTree.getDependencyPath(pidx, aidx, parents);
    }
    
    public List<Pair<Integer, Dir>> getDpPathPred() {
        return dpPathPred;
    }
    
    public List<Pair<Integer, Dir>> getDpPathArg() {
        return this.dpPathArg;
    }
    
    public List<Pair<Integer, Dir>> getDpPathShare() {
        return dpPathShare;
    }
    
    private void cacheDpPathShare(int pidx, int aidx, FeaturizedToken zhaoPred, FeaturizedToken zhaoArg) {

        /* ZHAO:  Leading two paths to the root from the predicate and the argument, respectively, 
         * the common part of these two paths will be dpPathShare. */
        List<Pair<Integer, Dir>> argRootPath = zhaoArg.getRootPath();
        List<Pair<Integer, Dir>> predRootPath = zhaoPred.getRootPath();
        int i = argRootPath.size() - 1;
        int j = predRootPath.size() - 1;
        Pair<Integer,DepTree.Dir> argP = argRootPath.get(i);
        Pair<Integer,DepTree.Dir> predP = predRootPath.get(j);
        while (argP.equals(predP)) {
            this.dpPathShare.add(argP);
            if (i == 0 || j == 0) {
                break;
            }
            i--;
            j--;
            argP = argRootPath.get(i);
            predP = predRootPath.get(j);
        }
        /* ZHAO:  Assume that dpPathShare starts from a node r', 
         * then dpPathPred is from the predicate to r', and dpPathArg is from the argument to r'. */
        // Reverse, so path goes towards the root.
        Collections.reverse(this.dpPathShare);
        int r;
        if (this.dpPathShare.isEmpty()) {
            r = -1;
            this.dpPathPred = new ArrayList<Pair<Integer, Dir>>();
            this.dpPathArg = new ArrayList<Pair<Integer, Dir>>();
        } else {
            r = this.dpPathShare.get(0).get1();
            this.dpPathPred = DepTree.getDependencyPath(pidx, r, parents);
            this.dpPathArg = DepTree.getDependencyPath(aidx, r, parents);
        }
    }
    
    public ArrayList<Integer> getLinePath() {
        return linePath;
    }
    
    private void cacheLinePath(int pidx, int aidx) {
        int startIdx;
        int endIdx; 
        if (pidx < aidx) {
            startIdx = pidx;
            endIdx = aidx;
        } else {
            startIdx = aidx;
            endIdx = pidx;
        }
        while (startIdx < endIdx) {
            this.linePath.add(startIdx);
            startIdx++;
        }
        
    }
    
}