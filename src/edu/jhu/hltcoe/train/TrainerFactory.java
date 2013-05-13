package edu.jhu.hltcoe.train;

import java.io.File;

import org.apache.commons.cli.ParseException;

import edu.jhu.hltcoe.data.DepTreebank;
import edu.jhu.hltcoe.eval.DependencyParserEvaluator;
import edu.jhu.hltcoe.gridsearch.BfsComparator;
import edu.jhu.hltcoe.gridsearch.DfsNodeOrderer;
import edu.jhu.hltcoe.gridsearch.DfsNodeOrderer.DfsNodeOrdererPrm;
import edu.jhu.hltcoe.gridsearch.LazyBranchAndBoundSolver.LazyBnbSolverFactory;
import edu.jhu.hltcoe.gridsearch.LazyBranchAndBoundSolver.LazyBnbSolverPrm;
import edu.jhu.hltcoe.gridsearch.NodeOrderer;
import edu.jhu.hltcoe.gridsearch.PlungingBfsNodeOrderer;
import edu.jhu.hltcoe.gridsearch.PlungingBfsNodeOrderer.PlungingBfsNodeOrdererPrm;
import edu.jhu.hltcoe.gridsearch.PqNodeOrderer;
import edu.jhu.hltcoe.gridsearch.cpt.BasicCptBoundsDeltaFactory;
import edu.jhu.hltcoe.gridsearch.cpt.CptBoundsDeltaFactory;
import edu.jhu.hltcoe.gridsearch.cpt.FullStrongVariableSelector;
import edu.jhu.hltcoe.gridsearch.cpt.LpSumToOneBuilder.CutCountComputer;
import edu.jhu.hltcoe.gridsearch.cpt.LpSumToOneBuilder.LpStoBuilderPrm;
import edu.jhu.hltcoe.gridsearch.cpt.MidpointVarSplitter;
import edu.jhu.hltcoe.gridsearch.cpt.MidpointVarSplitter.MidpointChoice;
import edu.jhu.hltcoe.gridsearch.cpt.Projections.ProjectionsPrm;
import edu.jhu.hltcoe.gridsearch.cpt.Projections.ProjectionsPrm.ProjectionType;
import edu.jhu.hltcoe.gridsearch.cpt.PseudocostVariableSelector;
import edu.jhu.hltcoe.gridsearch.cpt.RandomVariableSelector;
import edu.jhu.hltcoe.gridsearch.cpt.RegretVariableSelector;
import edu.jhu.hltcoe.gridsearch.cpt.VariableSelector;
import edu.jhu.hltcoe.gridsearch.cpt.VariableSplitter;
import edu.jhu.hltcoe.gridsearch.dmv.BasicDmvProjector.DmvProjectorFactory;
import edu.jhu.hltcoe.gridsearch.dmv.BasicDmvProjector.DmvProjectorPrm;
import edu.jhu.hltcoe.gridsearch.dmv.DmvDantzigWolfeRelaxation.DmvDwRelaxPrm;
import edu.jhu.hltcoe.gridsearch.dmv.DmvDantzigWolfeRelaxation.DmvRelaxationFactory;
import edu.jhu.hltcoe.gridsearch.dmv.DmvObjective.DmvObjectivePrm;
import edu.jhu.hltcoe.gridsearch.dmv.DmvRltRelaxation.DmvRltRelaxPrm;
import edu.jhu.hltcoe.gridsearch.dmv.DmvSolFactory.DmvSolFactoryPrm;
import edu.jhu.hltcoe.gridsearch.dmv.DmvSolutionEvaluator;
import edu.jhu.hltcoe.gridsearch.dmv.ResDmvDantzigWolfeRelaxation.ResDmvDwRelaxPrm;
import edu.jhu.hltcoe.gridsearch.dmv.ViterbiEmDmvProjector.ViterbiEmDmvProjectorPrm;
import edu.jhu.hltcoe.gridsearch.dr.DimReducer.ConstraintConversion;
import edu.jhu.hltcoe.gridsearch.dr.DimReducer.DimReducerPrm;
import edu.jhu.hltcoe.gridsearch.dr.DimReducer.SamplingDistribution;
import edu.jhu.hltcoe.gridsearch.randwalk.DepthStratifiedBnbNodeSampler.DepthStratifiedBnbSamplerPrm;
import edu.jhu.hltcoe.gridsearch.randwalk.DfsRandChildAtDepthNodeOrderer;
import edu.jhu.hltcoe.gridsearch.randwalk.DfsRandWalkNodeOrderer;
import edu.jhu.hltcoe.gridsearch.randwalk.RandWalkBnbNodeSampler.RandWalkBnbSamplerPrm;
import edu.jhu.hltcoe.gridsearch.rlt.Rlt.RltPrm;
import edu.jhu.hltcoe.gridsearch.rlt.filter.MaxNumRltRowAdder;
import edu.jhu.hltcoe.gridsearch.rlt.filter.RandPropRltRowAdder;
import edu.jhu.hltcoe.ilp.IlpSolverFactory;
import edu.jhu.hltcoe.ilp.IlpSolverFactory.IlpSolverId;
import edu.jhu.hltcoe.model.FixableModelFactory;
import edu.jhu.hltcoe.model.dmv.DmvModel;
import edu.jhu.hltcoe.model.dmv.DmvModelFactory;
import edu.jhu.hltcoe.model.dmv.FixableDmvModelFactory;
import edu.jhu.hltcoe.model.dmv.RandomDmvModelFactory;
import edu.jhu.hltcoe.model.dmv.SupervisedDmvModelFactory;
import edu.jhu.hltcoe.model.dmv.UniformDmvModelFactory;
import edu.jhu.hltcoe.parse.DeltaGenerator;
import edu.jhu.hltcoe.parse.DmvCkyParser;
import edu.jhu.hltcoe.parse.FactorDeltaGenerator;
import edu.jhu.hltcoe.parse.FixedIntervalDeltaGenerator;
import edu.jhu.hltcoe.parse.IlpFormulation;
import edu.jhu.hltcoe.parse.IlpViterbiParser;
import edu.jhu.hltcoe.parse.IlpViterbiParserWithDeltas;
import edu.jhu.hltcoe.parse.IlpViterbiSentenceParser;
import edu.jhu.hltcoe.parse.InitializedIlpViterbiParserWithDeltas;
import edu.jhu.hltcoe.parse.ViterbiParser;
import edu.jhu.hltcoe.parse.relax.DmvParseLpBuilder.DmvParseLpBuilderPrm;
import edu.jhu.hltcoe.parse.relax.LpDmvRelaxedParser;
import edu.jhu.hltcoe.parse.relax.LpDmvRelaxedParser.LpDmvRelaxedParserPrm;
import edu.jhu.hltcoe.parse.relax.RelaxedParserWrapper;
import edu.jhu.hltcoe.parse.relax.RelaxedParserWrapper.RelaxedParserWrapperPrm;
import edu.jhu.hltcoe.train.BnBDmvTrainer.BnBDmvTrainerPrm;
import edu.jhu.hltcoe.train.DmvViterbiEMTrainer.DmvViterbiEMTrainerPrm;
import edu.jhu.hltcoe.train.LocalBnBDmvTrainer.LocalBnBDmvTrainerPrm;
import edu.jhu.hltcoe.util.cli.Opt;
import edu.jhu.hltcoe.util.cplex.CplexPrm;
import edu.jhu.hltcoe.util.cplex.CplexPrm.SimplexAlgorithm;

/**
 * Factory for constructing trainers (and other related objects).
 * 
 * @author mgormley
 */
public class TrainerFactory {

    @Opt(name = "algorithm", hasArg = true, description = "Inference algorithm")
    public static String algorithm = "viterbi";
    @Opt(name = "model", hasArg = true, description = "Model")
    public static String modelName = "dmv";
    @Opt(name = "parser", hasArg = true, description = "Parser")
    public static String parserName = "ilp-sentence";
    @Opt(name = "dwTempDir", hasArg = true, description = "(D-W only) For testing only. The temporary directory to which CPLEX files should be written")
    public static String dwTempDir = "";
    @Opt(name = "timeoutSeconds", hasArg = true, description = "The timeout in seconds for training run")
    public static double timeoutSeconds = Double.POSITIVE_INFINITY;
    
    // EM parameters.
    @Opt(name = "lambda", hasArg = true, description = "Value for add-lambda smoothing")
    public static double lambda = 0.1;
    @Opt(name = "iterations", hasArg = true, description = "Number of iterations")
    public static int iterations = 10;
    @Opt(name = "convergenceRatio", hasArg = true, description = "Convergence ratio")
    public static double convergenceRatio = 0.99999;
    @Opt(name = "numRestarts", hasArg = true, description = "Number of random restarts")
    public static int numRestarts = 0;
    
    // Initialization parameters.
    @Opt(name = "initWeights", hasArg = true, description = "Method for initializing the weights [uniform, random, supervised]")
    public static String initWeights = "uniform";
    @Opt(name = "offsetProb", hasArg = true, description = "How much to offset the bounds in probability space from the initial bounds point")
    public static double offsetProb = 1.0;
    @Opt(name = "probOfSkipCm", hasArg = true, description = "The probability of not bounding a particular variable")
    public static double probOfSkipCm = 0.0;
    
    // Branch-and-bound parameters.
    @Opt(name = "bnbTimeoutSeconds", hasArg = true, description = "[Viterbi-B&B only] The timeout in seconds for branch-and-bound")
    public static double bnbTimeoutSeconds = Double.POSITIVE_INFINITY;
    @Opt(name = "disableFathoming", hasArg = true, description = "Disables fathoming in branch-and-bound")
    public static boolean disableFathoming = false;
    @Opt(name = "epsilon", hasArg = true, description = "Suboptimality convergence criterion for branch-and-bound")
    public static double epsilon = 0.1;
    @Opt(name = "varSelection", hasArg = true, description = "Variable selection strategy for branching [full,regret,rand-uniform,rand-weighted]")
    public static String varSelection = "regret";
    @Opt(name = "varSplit", hasArg = true, description = "Variable splitting strategy for branching [half-prob, half-logprob]")
    public static String varSplit = "half-prob";
    @Opt(name = "nodeOrder", hasArg = true, description = "Strategy for node selection [bfs, dfs]")
    public static String nodeOrder = "bfs";
    @Opt(name = "localRelativeGapThreshold", hasArg = true, description = "The plunge's stopping threshold for local relative gap.")
    public static double localRelativeGapThreshold;

    // Knuth's random walk sampler parameters.
    @Opt(name = "maxRandWalkSamples", hasArg = true, description = "The maximum number of random walks to take when estimating tree size/time.")
    public static int maxRandWalkSamples = 10000;
    
    // Relaxation parameters.
    @Opt(name = "relaxation", hasArg = true, description = "Relaxation [dw,dw-res,rlt]")
    public static String relaxation = "dw";
    @Opt(name = "maxSimplexIterations", hasArg = true, description = "(D-W only) The maximum number of simplex iterations")
    public static int maxSimplexIterations = 2100000000;
    @Opt(name = "maxDwIterations", hasArg = true, description = "(D-W only) The maximum number of dantzig-wolfe algorithm iterations")
    public static int maxDwIterations = 1000;
    @Opt(name = "maxSetSizeToConstrain", hasArg = true, description = "(STO only) The maximum size of sets to contrain to be <= 1.0")
    public static int maxSetSizeToConstrain = 0;
    @Opt(name = "maxCutRounds", hasArg = true, description = "(D-W only) The maximum number of rounds to add cuts")
    public static int maxCutRounds = 100;
    @Opt(name = "rootMaxCutRounds", hasArg = true, description = "(D-W only) The maximum number of rounds to add cuts for the root node")
    public static int rootMaxCutRounds = maxCutRounds;
    @Opt(name = "minSumForCuts", hasArg = true, description = "(STO only) The minimum threshold at which to stop adding cuts")
    public static double minSumForCuts = 1.01;
    @Opt(name = "maxStoCuts", hasArg = true, description = "(STO only) The maximum number of sum-to-one cuts")
    public static int maxStoCuts = 1000;
    
    // MILP / LP Solver parameters.
    @Opt(name = "ilpSolver", hasArg = true, description = "The ILP solver to use")
    public static String ilpSolver = "cplex";
    @Opt(name = "simplexAlgorithm", hasArg = true, description = "The simplex algorithm to use in CPLEX.")
    public static SimplexAlgorithm simplexAlgorithm = SimplexAlgorithm.AUTO;
    @Opt(name = "threads", hasArg = true, description = "Number of threads for parallel impl")
    public static int numThreads = 2;
    @Opt(name = "ilpWorkMemMegs", hasArg = true, description = "The working memory allotted for the ILP solver in megabytes")
    public static double ilpWorkMemMegs = 512.0;
    
    // Parse formulation parameters.
    @Opt(name = "formulation", hasArg = true, description = "ILP formulation for parsing")
    public static IlpFormulation formulation = IlpFormulation.DP_PROJ;
    @Opt(name = "universalPostCons", hasArg = true, description = "Whether to add the universal linguistic constraints.")
    public static boolean universalPostCons = false;
    @Opt(name = "universalMinProp", hasArg = true, description = "The proportion of edges that must be from the shiny set.")
    public static double universalMinProp = 0.8;
    @Opt(name = "universalMinPropAddend", hasArg = true, description = "A proportion to add to the universalMinProp when running the relaxed parser.")
    public static double universalMinPropAddend = 0.0;
    @Opt(hasArg = true, description = "Whether to include extra parse constraints to tighten the relaxation.")
    public static boolean inclExtraParseCons = false;

    // Grid-search parse formulation parameters.
    @Opt(name = "deltaGenerator", hasArg = true, description = "Delta generator")
    public static String deltaGenerator = "fixed";
    @Opt(name = "interval", hasArg = true, description = "Only for fixed-interval delta generator")
    public static double interval = 0.01;
    @Opt(name = "factor", hasArg = true, description = "Only for factor delta generator")
    public static double factor = 1.1;
    @Opt(name = "numPerSide", hasArg = true, description = "For symmetric delta generators")
    public static int numPerSide = 2;
    
    // B&B initial solution parameters. 
    @Opt(name = "initSolNumRestarts", hasArg = true, description = "(B&B only) Number of random restarts for initial solution.")
    public static int initSolNumRestarts = 9;
    @Opt(name = "initSolTimeoutSeconds", hasArg = true, description = "(B&B only) The timeout in seconds for finding an initial solution.")
    public static double initSolTimeoutSeconds = Double.POSITIVE_INFINITY;
    
    // Projection parameters.
    @Opt(name = "vemProjNumRestarts", hasArg = true, description = "(B&B only) Number of random restarts for the viterbi EM projector.")
    public static int vemProjNumRestarts = 0;
    @Opt(name = "vemProjPropImproveTreebank", hasArg = true, description = "(B&B only) The proportion of nodes at which to improve the treebank projection with viterbi EM.")
    public static double vemProjPropImproveTreebank = 0;
    @Opt(name = "vemProjPropImproveModel", hasArg = true, description = "(B&B only) The proportion of nodes at which to improve the model projection with viterbi EM.")
    public static double vemProjPropImproveModel = 0;
    @Opt(name = "projType", hasArg = true, description = "(B&B only) The type of projection to use.")
    public static ProjectionType projType = ProjectionType.UNBOUNDED_MIN_EUCLIDEAN;
    @Opt(hasArg = true, description = "(B&B only) The type of projection algorithm to use.")
    public static ProjectionAlgo projAlgo = ProjectionAlgo.VEM;
    
    public static enum ProjectionAlgo { VEM, BASIC }
    
    // RLT parameters.
    @Opt(name = "envelopeOnly", hasArg = true, description = "Whether to use only the convex/concave envelope for the RLT relaxation")
    public static boolean envelopeOnly = true;
    @Opt(name = "rltFilter", hasArg = true, description = "RLT filter type [obj-var, prop, max]")
    public static String rltFilter = "obj-var";
    @Opt(name = "rltInitProp", hasArg = true, description = "(prop only) Proportion of initial rows to accept.")
    public static double rltInitProp = 0.1;
    @Opt(name = "rltCutProp", hasArg = true, description = "(prop only) Proportion of cut rows to accept.")
    public static double rltCutProp = 0.1;
    @Opt(name = "rltInitMax", hasArg = true, description = "(max only) Max number of initial rows to accept.")
    public static int rltInitMax = 10000;
    @Opt(name = "rltCutMax", hasArg = true, description = "(max only) Max number of cut rows to accept.")
    public static int rltCutMax = 1000;
    // TODO: Change rltNames to lpNames.
    @Opt(name = "rltNames", hasArg = true, description = "Whether to set RLT variable/constraint names.")
    public static boolean rltNames = false;
    @Opt(name = "addBindingCons", hasArg = true, description = "Whether to add binding constraints as factors to RLT.")
    public static boolean addBindingCons = false;
    
    // Dimensionality Reduction parameters.
    @Opt(hasArg = true, description = "The max number of dimensionality reduced constraints.")
    public static int drMaxCons = Integer.MAX_VALUE;
    @Opt(hasArg = true, description = "The max number of nonzeros per row of the projection matrix.")
    public static int drMaxNonZerosPerRow = Integer.MAX_VALUE;
    @Opt(hasArg = true, description = "The sampling distribution to use for nonzeros in the projection matrix.")
    public static SamplingDistribution drSamplingDist = SamplingDistribution.ALL_ONES;
    @Opt(hasArg = true, description = "The alpha parameter for the symmetric Dirichlet distributions.")
    public static double drAlpha = 1;
    @Opt(hasArg = true, description = "The delta for testing equality with zero after the multiplication for projection. " +
    		"Caution should be used when adjusting this paramter as it may cause infeasibilities.")
    public static double drMultZeroDelta = 1e-13;
    @Opt(hasArg = true, description = "The delta for testing equality with zero when sampling the projection matrix.")
    public static double drSampZeroDelta = 1e-2;
    @Opt(hasArg = true, description = "Whether to renormalize the rows before projecting.")
    public static boolean drRenormalize = true;
    @Opt(hasArg = true, description = "Whether to include the bounds in projection.")
    public static boolean drIncludeBounds = false;
    @Opt(hasArg = true, description = "Whether to use the identity matrix for projection (sanity check).")
    public static boolean drUseIdentityMatrix = false;
    @Opt(hasArg = true, description = "The type of constraint conversion.")
    public static ConstraintConversion drConversion = ConstraintConversion.SEPARATE_EQ_AND_LEQ;
        
    public static ViterbiParser getEvalParser() {
        return new DmvCkyParser(getDmvObjectivePrm());
    }
    
    public static DmvRelaxationFactory getDmvRelaxationFactory() throws ParseException {
        CplexPrm cplexPrm = getCplexPrm();

        ProjectionsPrm projPrm = getProjectionsPrm(); 

        LpStoBuilderPrm stoPrm = new LpStoBuilderPrm();
        stoPrm.initCutCountComp = new CutCountComputer();
        stoPrm.maxSetSizeToConstrain = maxSetSizeToConstrain;
        stoPrm.minSumForCuts = minSumForCuts;
        stoPrm.maxStoCuts = maxStoCuts;
        stoPrm.projPrm = projPrm;
        
        DmvObjectivePrm objPrm = getDmvObjectivePrm();
        
        DmvRelaxationFactory relaxFactory;
        File dwTemp = getDwTempDir();
        if (relaxation.equals("dw")) {
            DmvDwRelaxPrm dwPrm = new DmvDwRelaxPrm();
            dwPrm.tempDir = dwTemp;
            dwPrm.maxCutRounds = maxCutRounds;
            dwPrm.rootMaxCutRounds = rootMaxCutRounds;
            dwPrm.cplexPrm = cplexPrm;
            dwPrm.maxDwIterations = maxDwIterations;
            dwPrm.stoPrm = stoPrm;
            dwPrm.objPrm = objPrm;
            relaxFactory = dwPrm;
        } else if (relaxation.equals("dw-res")) {
            ResDmvDwRelaxPrm dwPrm = new ResDmvDwRelaxPrm();
            dwPrm.tempDir = dwTemp;
            dwPrm.maxCutRounds = maxCutRounds;
            dwPrm.rootMaxCutRounds = rootMaxCutRounds;
            dwPrm.cplexPrm = cplexPrm;
            dwPrm.maxDwIterations = maxDwIterations;
            dwPrm.projPrm = projPrm;
            dwPrm.objPrm = objPrm;
            relaxFactory = dwPrm;
        } else if (relaxation.equals("rlt")) {
            RltPrm rltPrm = new RltPrm();
            rltPrm.nameRltVarsAndCons = false;
            rltPrm.envelopeOnly = envelopeOnly;
            rltPrm.nameRltVarsAndCons = rltNames;
            
            DmvParseLpBuilderPrm parsePrm = getDmvParseLpBuilderPrm();

            DimReducerPrm drPrm = new DimReducerPrm();
            drPrm.drMaxCons = drMaxCons;
            drPrm.multZeroDelta = drMultZeroDelta;
            drPrm.sampZeroDelta = drSampZeroDelta;
            drPrm.renormalize = drRenormalize;
            drPrm.maxNonZerosPerRow = drMaxNonZerosPerRow;
            drPrm.dist = drSamplingDist;
            drPrm.alpha = drAlpha;
            drPrm.includeBounds = drIncludeBounds;
            drPrm.useIdentityMatrix = drUseIdentityMatrix;
            drPrm.conversion = drConversion;
            drPrm.tempDir = dwTemp;
            
            DmvRltRelaxPrm rrPrm = new DmvRltRelaxPrm();
            rrPrm.tempDir = dwTemp;
            rrPrm.maxCutRounds = maxCutRounds;
            rrPrm.rootMaxCutRounds = rootMaxCutRounds;
            rrPrm.addBindingCons = addBindingCons;
            rrPrm.cplexPrm = cplexPrm;
            rrPrm.rltPrm = rltPrm;
            rrPrm.stoPrm = stoPrm;
            rrPrm.parsePrm = parsePrm;
            rrPrm.drPrm = drPrm;
            rrPrm.objPrm = objPrm;

            rrPrm.objVarFilter = false;
            if (rltFilter.equals("obj-var")) {
                rrPrm.objVarFilter = true;
                rltPrm.factorFilter = null;
                rltPrm.rowAdder = null;
            } else if (rltFilter.equals("prop")) {
                rltPrm.rowAdder = new RandPropRltRowAdder(rltInitProp, rltCutProp);
            } else if (rltFilter.equals("max")) {
                rltPrm.rowAdder = new MaxNumRltRowAdder(rltInitMax, rltCutMax);
            } else {
                throw new ParseException("RLT filter type not supported: " + rltFilter);
            }
            relaxFactory = rrPrm;
        } else {
            throw new ParseException("Relaxation not supported: " + relaxation);
        }
        return relaxFactory;
    }

    private static DmvParseLpBuilderPrm getDmvParseLpBuilderPrm() {
        DmvParseLpBuilderPrm parsePrm = new DmvParseLpBuilderPrm();
        parsePrm.universalMinProp = universalMinProp + universalMinPropAddend;
        parsePrm.universalPostCons = universalPostCons;
        parsePrm.inclExtraCons = inclExtraParseCons;
        parsePrm.formulation = formulation;
        parsePrm.setNames = TrainerFactory.rltNames;
        return parsePrm;
    }

    private static File getDwTempDir() {
        File dwTemp = dwTempDir.equals("") ? null : new File(dwTempDir);
        return dwTemp;
    }

    private static CplexPrm getCplexPrm() {
        double simplexTimeout = algorithm.equals("viterbi-bnb") ? bnbTimeoutSeconds : timeoutSeconds;
        CplexPrm cplexPrm = new CplexPrm(ilpWorkMemMegs, numThreads, maxSimplexIterations, 
                simplexTimeout, simplexAlgorithm.cplexId);
        return cplexPrm;
    }

    public static Trainer getTrainer(DepTreebank goldTreebank, DmvModel goldModel) throws ParseException {
        if (!modelName.equals("dmv")) {
            throw new ParseException("Model not supported: " + modelName);
        }

        boolean isBnbAlgorithm = (algorithm.equals("bnb") || algorithm.equals("viterbi-bnb")
                || algorithm.equals("bnb-depth-stratified") || algorithm.equals("bnb-rand-walk"));
        DmvRelaxationFactory relaxFactory = null;
        if (isBnbAlgorithm) {
            relaxFactory = getDmvRelaxationFactory();
        }

        DependencyParserEvaluator parserEvaluator = new DependencyParserEvaluator(getEvalParser(), goldTreebank, "train");

        Trainer trainer = null;
        DmvViterbiEMTrainer viterbiTrainer = null;
        if (algorithm.equals("viterbi") || algorithm.equals("viterbi-bnb")) {
            ViterbiParser parser = getViterbiParser();
            DmvModelFactory modelFactory = getDmvModelFactory(goldTreebank, goldModel);

            if (algorithm.equals("viterbi")) {
                DmvViterbiEMTrainerPrm vtPrm = new DmvViterbiEMTrainerPrm(iterations, convergenceRatio, numRestarts,
                        timeoutSeconds, lambda, parserEvaluator, parser, modelFactory);
                trainer = new DmvViterbiEMTrainer(vtPrm);
            }
            if (algorithm.equals("viterbi-bnb")) {
                // Use zero random restarts, no timeout, and no evaluator for
                // local search with large neighborhoods.
                DmvViterbiEMTrainerPrm vtPrm = new DmvViterbiEMTrainerPrm(iterations, convergenceRatio, 0,
                        Double.POSITIVE_INFINITY, lambda, null, parser, modelFactory);
                viterbiTrainer = new DmvViterbiEMTrainer(vtPrm);
            }
        }

        CptBoundsDeltaFactory brancher = null;
        if (isBnbAlgorithm) {
            VariableSplitter varSplitter;
            if (varSplit.equals("half-prob")) {
                varSplitter = new MidpointVarSplitter(MidpointChoice.HALF_PROB);
            } else if (varSplit.equals("half-logprob")) {
                varSplitter = new MidpointVarSplitter(MidpointChoice.HALF_LOGPROB);
            } else {
                throw new ParseException("Variable splitting strategy not supported: " + varSplit);
            }

            VariableSelector varSelector;
            if (varSelection.equals("full")) {
                varSelector = new FullStrongVariableSelector(varSplitter);
            } else if (varSelection.equals("pseudocost")) {
                varSelector = new PseudocostVariableSelector(varSplitter);
            } else if (varSelection.equals("regret")) {
                varSelector = new RegretVariableSelector();
            } else if (varSelection.equals("rand-uniform")) {
                varSelector = new RandomVariableSelector(true);
            } else if (varSelection.equals("rand-weighted")) {
                varSelector = new RandomVariableSelector(false);
            } else {
                throw new ParseException("Variable selection strategy not supported: " + varSelection);
            }

            brancher = new BasicCptBoundsDeltaFactory(varSelector, varSplitter);
        }

        NodeOrderer nodeOrderer = null;
        if (nodeOrder.equals("bfs")) {
            nodeOrderer = new PqNodeOrderer(new BfsComparator());
        } else if (nodeOrder.equals("dfs")) {
            DfsNodeOrdererPrm prm = new DfsNodeOrdererPrm();
            nodeOrderer = new DfsNodeOrderer(prm);
        } else if (nodeOrder.equals("plunging-bfs")) {
            PlungingBfsNodeOrdererPrm prm = new PlungingBfsNodeOrdererPrm();
            prm.localRelativeGapThreshold = localRelativeGapThreshold;
            nodeOrderer = new PlungingBfsNodeOrderer(prm);
        } else if (nodeOrder.equals("dfs-rand")) {
            nodeOrderer = new DfsRandChildAtDepthNodeOrderer(60);
        } else if (nodeOrder.equals("dfs-randwalk")) {
            nodeOrderer = new DfsRandWalkNodeOrderer(60);
        }
        DmvSolFactoryPrm initSolPrm = getDmvSolFactoryPrm(goldTreebank, goldModel);

        DmvProjectorFactory projectorFactory = getDmvProjectorFactory(goldTreebank, goldModel);

        LazyBnbSolverPrm bnbPrm = new LazyBnbSolverPrm();
        bnbPrm.disableFathoming = disableFathoming;
        bnbPrm.epsilon = epsilon;
        bnbPrm.evaluator = new DmvSolutionEvaluator(parserEvaluator);
        bnbPrm.leafNodeOrderer = nodeOrderer;
        bnbPrm.timeoutSeconds = timeoutSeconds;

        if (algorithm.equals("viterbi-bnb")) {
            // Use a null evaluator so that the incumbent is not repeatedly
            // printed out.
            bnbPrm.evaluator = null;
            bnbPrm.timeoutSeconds = bnbTimeoutSeconds;
            LocalBnBDmvTrainerPrm lbPrm = new LocalBnBDmvTrainerPrm(viterbiTrainer, bnbPrm, brancher, relaxFactory,
                    projectorFactory, numRestarts, offsetProb, probOfSkipCm, timeoutSeconds, parserEvaluator, initSolPrm);
            trainer = new LocalBnBDmvTrainer(lbPrm);
        } else if (algorithm.equals("bnb") || algorithm.equals("bnb-rand-walk")
                || algorithm.equals("bnb-depth-stratified")) {

            LazyBnbSolverFactory bnbSolverFactory;
            if (algorithm.equals("bnb-rand-walk")) {
                RandWalkBnbSamplerPrm prm = new RandWalkBnbSamplerPrm();
                prm.maxSamples = maxRandWalkSamples ;
                prm.bnbPrm = bnbPrm;
                bnbSolverFactory = prm;
            } else if (algorithm.equals("bnb-depth-stratified")) {
                DepthStratifiedBnbSamplerPrm prm = new DepthStratifiedBnbSamplerPrm();
                prm.maxDepth = 60;
                prm.bnbPrm = bnbPrm;
                bnbSolverFactory = prm;
            } else if (algorithm.equals("bnb")) {
                bnbSolverFactory = bnbPrm;
            } else {
                throw new ParseException("Algorithm not supported: " + algorithm);
            }
            
            BnBDmvTrainerPrm bnbtPrm = new BnBDmvTrainerPrm();
            bnbtPrm.initSolPrm = initSolPrm;
            bnbtPrm.brancher = brancher;
            bnbtPrm.bnbSolverFactory = bnbSolverFactory;
            bnbtPrm.relaxFactory = relaxFactory;
            bnbtPrm.projectorFactory = projectorFactory;
            trainer = new BnBDmvTrainer(bnbtPrm);
        }

        if (trainer == null) {
            throw new ParseException("Algorithm not supported: " + algorithm);
        }

        return trainer;
    }

    private static ViterbiParser getViterbiParser() throws ParseException {
        ViterbiParser parser;
        IlpSolverFactory ilpSolverFactory = null;
        if (parserName.startsWith("ilp-")) {
            IlpSolverId ilpSolverId = IlpSolverId.getById(ilpSolver);
            ilpSolverFactory = new IlpSolverFactory(ilpSolverId, numThreads, ilpWorkMemMegs);
            // TODO: make this an option
            // ilpSolverFactory.setBlockFileWriter(new
            // DeltaParseBlockFileWriter(formulation));
        }

        if (parserName.equals("cky")) {
            parser = new DmvCkyParser(getDmvObjectivePrm());
        } else if (parserName.equals("relaxed")) {
            LpDmvRelaxedParserPrm lpParsePrm = new LpDmvRelaxedParserPrm();
            lpParsePrm.cplexPrm = getCplexPrm();
            lpParsePrm.parsePrm = getDmvParseLpBuilderPrm();
            lpParsePrm.tempDir = getDwTempDir();

            RelaxedParserWrapperPrm prm = new RelaxedParserWrapperPrm();
            prm.projPrm = getDmvProjectorPrm();
            prm.relaxedParser = new LpDmvRelaxedParser(lpParsePrm);
            prm.objPrm = getDmvObjectivePrm();
            
            parser = new RelaxedParserWrapper(prm);
        } else if (parserName.equals("ilp-sentence")) {
            parser = new IlpViterbiSentenceParser(formulation, ilpSolverFactory);
        } else if (parserName.equals("ilp-corpus")) {
            parser = new IlpViterbiParser(formulation, ilpSolverFactory);
        } else if (parserName.equals("ilp-deltas") || parserName.equals("ilp-deltas-init")) {
            DeltaGenerator deltaGen;
            if (deltaGenerator.equals("fixed-interval")) {
                deltaGen = new FixedIntervalDeltaGenerator(interval, numPerSide);
            } else if (deltaGenerator.equals("factor")) {
                deltaGen = new FactorDeltaGenerator(factor, numPerSide);
            } else {
                throw new ParseException("Delta generator not supported: " + deltaGenerator);
            }
            if (parserName.equals("ilp-deltas")) {
                parser = new IlpViterbiParserWithDeltas(formulation, ilpSolverFactory, deltaGen);
            } else if (parserName.equals("ilp-deltas-init")) {
                parser = new InitializedIlpViterbiParserWithDeltas(formulation, ilpSolverFactory, deltaGen,
                        ilpSolverFactory);
            } else {
                throw new ParseException("Parser not supported: " + parserName);
            }
        } else {
            throw new ParseException("Parser not supported: " + parserName);
        }
        return parser;
    }

    private static DmvModelFactory getDmvModelFactory(DepTreebank goldTreebank,
            DmvModel goldModel) throws ParseException {
        DmvModelFactory modelFactory;
        if (initWeights.equals("uniform")) {
            modelFactory = new UniformDmvModelFactory();
        } else if (initWeights.equals("random")) {
            modelFactory = new RandomDmvModelFactory(lambda);
        } else if (initWeights.equals("supervised")) {
            modelFactory = new SupervisedDmvModelFactory(goldTreebank);
        } else if (initWeights.equals("gold")) {
            modelFactory = new FixableDmvModelFactory(null);
            ((FixableModelFactory) modelFactory).fixModel(goldModel);
        } else {
            throw new ParseException("initWeights not supported: " + initWeights);
        }
        return modelFactory;
    }

    public static DmvObjectivePrm getDmvObjectivePrm() {
        DmvObjectivePrm prm = new DmvObjectivePrm();
        prm.universalPostCons = universalPostCons;
        prm.universalMinProp = universalMinProp;
        prm.shinyEdges = null;
        return prm;
    }

    public static DmvSolFactoryPrm getDmvSolFactoryPrm(DepTreebank goldTreebank, DmvModel goldModel) throws ParseException {        
        ViterbiParser parser = getViterbiParser();
        DmvModelFactory modelFactory = getDmvModelFactory(goldTreebank, goldModel);
        
        DmvSolFactoryPrm initSolPrm = new DmvSolFactoryPrm();
        initSolPrm.vemPrm = new DmvViterbiEMTrainerPrm(iterations, convergenceRatio, initSolNumRestarts,
                initSolTimeoutSeconds, lambda, null, parser, modelFactory);
        return initSolPrm;
    }

    public static DmvProjectorFactory getDmvProjectorFactory(DepTreebank goldTreebank, DmvModel goldModel) throws ParseException {
        DmvProjectorPrm dprojPrm = getDmvProjectorPrm();
        dprojPrm.objPrm = getDmvObjectivePrm();
        
        if (projAlgo == ProjectionAlgo.BASIC) {
            return dprojPrm;
        } else if (projAlgo == ProjectionAlgo.VEM) {
            // Other constants.
            final double vemProjTimeoutSeconds = timeoutSeconds / 10.0;
            
            ViterbiParser parser = getViterbiParser();
            DmvModelFactory modelFactory = getDmvModelFactory(goldTreebank, goldModel);
            
            ViterbiEmDmvProjectorPrm vedpPrm = new ViterbiEmDmvProjectorPrm();
            vedpPrm.proportionViterbiImproveTreebank = vemProjPropImproveTreebank;
            vedpPrm.proportionViterbiImproveModel = vemProjPropImproveModel;
            vedpPrm.dprojPrm = dprojPrm;
            vedpPrm.vemPrm = new DmvViterbiEMTrainerPrm(iterations, convergenceRatio, vemProjNumRestarts,
                    vemProjTimeoutSeconds, lambda, null, parser, modelFactory);
            return vedpPrm;
        } else {
            throw new RuntimeException("Unhandled projection algorithm: " + projAlgo);
        }
    }

    private static DmvProjectorPrm getDmvProjectorPrm() {
        ProjectionsPrm projPrm = getProjectionsPrm();
        DmvProjectorPrm dprojPrm = new DmvProjectorPrm();
        dprojPrm.projPrm = projPrm;
        return dprojPrm;
    }

    private static ProjectionsPrm getProjectionsPrm() {
        ProjectionsPrm projPrm = new ProjectionsPrm();
        projPrm.lambdaSmoothing = lambda;
        projPrm.type = projType;
        return projPrm;
    }

}
