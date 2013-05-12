package edu.jhu.hltcoe;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

import edu.jhu.hltcoe.data.DepTreebank;
import edu.jhu.hltcoe.data.DepTreebankReader;
import edu.jhu.hltcoe.data.DepTreebankReader.DatasetType;
import edu.jhu.hltcoe.data.Label;
import edu.jhu.hltcoe.data.Sentence;
import edu.jhu.hltcoe.data.SentenceCollection;
import edu.jhu.hltcoe.data.TaggedWord;
import edu.jhu.hltcoe.eval.DependencyParserEvaluator;
import edu.jhu.hltcoe.eval.Evaluator;
import edu.jhu.hltcoe.gridsearch.dmv.BasicDmvProjector.DmvProjectorFactory;
import edu.jhu.hltcoe.gridsearch.dmv.DmvDantzigWolfeRelaxation.DmvRelaxationFactory;
import edu.jhu.hltcoe.gridsearch.dmv.DmvProblemNode;
import edu.jhu.hltcoe.gridsearch.dmv.DmvProjector;
import edu.jhu.hltcoe.gridsearch.dmv.DmvRelaxation;
import edu.jhu.hltcoe.gridsearch.dmv.DmvRelaxedSolution;
import edu.jhu.hltcoe.gridsearch.dmv.DmvSolFactory;
import edu.jhu.hltcoe.gridsearch.dmv.DmvSolFactory.InitSol;
import edu.jhu.hltcoe.gridsearch.dmv.DmvSolution;
import edu.jhu.hltcoe.gridsearch.dmv.IndexedDmvModel;
import edu.jhu.hltcoe.model.Model;
import edu.jhu.hltcoe.model.dmv.DmvDepTreeGenerator;
import edu.jhu.hltcoe.model.dmv.DmvModel;
import edu.jhu.hltcoe.model.dmv.SimpleStaticDmvModel;
import edu.jhu.hltcoe.parse.ViterbiParser;
import edu.jhu.hltcoe.train.BnBDmvTrainer;
import edu.jhu.hltcoe.train.DmvTrainCorpus;
import edu.jhu.hltcoe.train.LocalBnBDmvTrainer;
import edu.jhu.hltcoe.train.Trainer;
import edu.jhu.hltcoe.train.TrainerFactory;
import edu.jhu.hltcoe.util.Alphabet;
import edu.jhu.hltcoe.util.Prng;
import edu.jhu.hltcoe.util.Timer;
import edu.jhu.hltcoe.util.cli.ArgParser;
import edu.jhu.hltcoe.util.cli.Opt;

public class PipelineRunner {
    
    private static final Logger log = Logger.getLogger(PipelineRunner.class);
    
    // Options not specific to the model
    @Opt(name = "seed", hasArg = true, description = "Pseudo random number generator seed for everything else.")
    public static long seed = Prng.DEFAULT_SEED;
    @Opt(name = "printModel", hasArg = true, description = "File to which we should print the model.")
    public static String printModel = null;
    @Opt(name = "relaxOnly", hasArg = true, description = "Flag indicating that only a relaxation should be run")
    public static boolean relaxOnly = false;

    // Options for data
    @Opt(name="train", hasArg=true, required=true, description="Training data.")
    public static String train = null;
    @Opt(name="trainType", hasArg=true, required=true, description="Type of training data.")
    public static DatasetType trainType = null;
    @Opt(name = "synthetic", hasArg = true, description = "Generate synthetic training data.")
    public static String synthetic = null;
    @Opt(name = "printSentences", hasArg = true, description = "File to which we should print the sentences.")
    public static String printSentences = null;
    @Opt(name = "syntheticSeed", hasArg = true, description = "Pseudo random number generator seed for synthetic data generation only.")
    public static long syntheticSeed = 123454321;
    @Opt(name = "propSupervised", hasArg = true, description = "Proportion of labeled/supervised training data.")
    public static double propSupervised = 0.0;

    // Options for test data
    @Opt(name="test", hasArg=true, description="Testing data.")
    public static String test = null;
    @Opt(name="testType", hasArg=true, description="Type of testing data.")
    public static DatasetType testType = null;
    //@Opt(name = "test", hasArg = true, description = "Test data.")
    //public static String test = null;
    @Opt(hasArg = true, description = "Maximum sentence length for test data.")
    public static int maxSentenceLengthTest = Integer.MAX_VALUE;

    // Options to restrict the initialization
    @Opt(name = "initBounds", hasArg = true, description = "How to initialize the bounds: [viterbi-em, gold, random, uniform, none]")
    public static InitSol initBounds = null;
    
    public PipelineRunner() {
    }

    public void run() throws ParseException, IOException {  
        
        // Get the training data
        DepTreebank trainTreebank;
        DmvModel goldModel = null;
        if (trainType == DatasetType.PTB || trainType == DatasetType.CONLL_X
                || trainType == DatasetType.CONLL_2009) {
            // Read the data and (maybe) reduce size of treebank
            log.info("Reading train data: " + train);
            Alphabet<Label> alphabet = new Alphabet<Label>();
            trainTreebank = DepTreebankReader.getTreebank(train, trainType, DepTreebankReader.maxSentenceLength, alphabet);
        } else if (trainType == DatasetType.SYNTHETIC) {
            if (synthetic == null) {
                throw new ParseException("--synthetic must be specified");
            }
            if (synthetic.equals("two")) {
                goldModel = SimpleStaticDmvModel.getTwoPosTagInstance();
            } else if (synthetic.equals("three")) {
                goldModel = SimpleStaticDmvModel.getThreePosTagInstance();
            } else if (synthetic.equals("alt-three")) {
                goldModel = SimpleStaticDmvModel.getAltThreePosTagInstance();
            } else {
                throw new ParseException("Unknown synthetic type: " + synthetic);
            }
            DmvDepTreeGenerator generator = new DmvDepTreeGenerator(goldModel, syntheticSeed);
            trainTreebank = generator.getTreebank(DepTreebankReader.maxNumSentences);
        } else {
            throw new ParseException("Either the option --train or --synthetic must be specified");
        }
        
        // Divide into labeled and unlabeled data.
        DmvTrainCorpus trainCorpus = new DmvTrainCorpus(trainTreebank, propSupervised); 
            
        log.info("Number of unlabeled train sentences: " + trainCorpus.getNumUnlabeled());
        log.info("Number of labeled train sentences: " + trainCorpus.getNumLabeled());
        log.info("Number of train sentences: " + trainTreebank.size());
        log.info("Number of train tokens: " + trainTreebank.getNumTokens());
        log.info("Number of train types: " + trainTreebank.getNumTypes());
                
        // Print train sentences to a file
        printSentences(trainTreebank);
        
        // Get the test data
        DepTreebank testTreebank = null;
        if (test != null) {
            // Read the data and (maybe) reduce size of treebank
            log.info("Reading test data: " + test);
           
            testTreebank = DepTreebankReader.getTreebank(test, testType, maxSentenceLengthTest, trainTreebank.getAlphabet());

            log.info("Number of test sentences: " + testTreebank.size());
            log.info("Number of test tokens: " + testTreebank.getNumTokens());
            log.info("Number of test types: " + testTreebank.getNumTypes());
        }
        
        if (relaxOnly) {
            DmvSolFactory initSolFactory = new DmvSolFactory(TrainerFactory.getDmvSolFactoryPrm(trainTreebank, goldModel));
            DmvSolution initSol = initSolFactory.getInitFeasSol(trainCorpus);
            DmvRelaxationFactory relaxFactory = TrainerFactory.getDmvRelaxationFactory();
            DmvRelaxation relax = relaxFactory.getInstance(trainCorpus, initSol);
            DmvSolution initBoundsSol = updateBounds(trainCorpus, relax, trainTreebank, goldModel);
            Timer timer = new Timer();
            timer.start();
            DmvProblemNode rootNode = new DmvProblemNode(null);
            DmvRelaxedSolution relaxSol = (DmvRelaxedSolution) relax.getRelaxedSolution(rootNode);
            timer.stop();
            log.info("relaxTime(ms): " + timer.totMs());
            log.info("relaxBound: " + relaxSol.getScore());
            if (initBoundsSol != null) {
                log.info("initBoundsSol: " + initBoundsSol.getScore());
                log.info("relative: " + Math.abs(relaxSol.getScore() - initBoundsSol.getScore()) / Math.abs(initBoundsSol.getScore()));
            }
            DmvProjectorFactory projectorFactory = TrainerFactory.getDmvProjectorFactory(trainTreebank, goldModel);
            DmvProjector dmvProjector = (DmvProjector) projectorFactory.getInstance(trainCorpus, relax);
            DmvSolution projSol = dmvProjector.getProjectedDmvSolution(relaxSol);
            if (projSol != null) {
                log.info("projLogLikelihood: " + projSol.getScore());
            } else {
                log.warn("projLogLikelihood: UNAVAILABLE");
            }
        } else {
            // Train the model
            log.info("Training model");
            Trainer trainer = TrainerFactory.getTrainer(trainTreebank, goldModel);
            if (trainer instanceof BnBDmvTrainer) {
                BnBDmvTrainer bnb = (BnBDmvTrainer) trainer;
                bnb.init(trainCorpus);
                updateBounds(trainCorpus, bnb.getRootRelaxation(), trainTreebank, goldModel);
                bnb.train();
            } else {
                trainer.train(trainCorpus);
            }
            Model model = trainer.getModel();
            
            // Evaluate the model on the training data
            log.info("Evaluating model on train");
            // Note: this parser must return the log-likelihood from parser.getParseWeight()
            ViterbiParser parser = TrainerFactory.getEvalParser();
            Evaluator trainEval = new DependencyParserEvaluator(parser, trainTreebank, "train");
            trainEval.evaluate(model);
            trainEval.print();
            
            // Evaluate the model on the test data
            if (testTreebank != null) {
                log.info("Evaluating model on test");
                Evaluator testEval = new DependencyParserEvaluator(parser, testTreebank, "test");
                testEval.evaluate(model);
                testEval.print();
            }
            
            // Print learned model to a file
            if (printModel != null) {
                BufferedWriter writer = new BufferedWriter(new FileWriter(printModel));
                writer.write("Learned Model:\n");
                writer.write(model.toString());
                writer.close();
            }
        }
    }

    // TODO: This should update the deltas of the root node.
    private DmvSolution updateBounds(DmvTrainCorpus trainCorpus, DmvRelaxation dw, DepTreebank trainTreebank, DmvModel trueModel) throws ParseException {
        if (initBounds != null) {
            // Initialize the bounds as a hypercube around some initial solution.
            double offsetProb = TrainerFactory.offsetProb;
            double probOfSkipCm = TrainerFactory.probOfSkipCm;
            
            DmvSolution goldSol = null;
            if (trueModel != null) { 
                IndexedDmvModel idm = new IndexedDmvModel(trainCorpus);
                goldSol = new DmvSolution(idm.getCmLogProbs(trueModel), idm, trainTreebank, Double.NaN);
            }
            // TODO: replace this with an TrainerFactory.getInitSolFactoryPrm();
            DmvSolution initBoundsSol = DmvSolFactory.getInitSol(initBounds, trainCorpus, dw, trainTreebank, goldSol);
            
            LocalBnBDmvTrainer.setBoundsFromInitSol(dw, initBoundsSol, offsetProb, probOfSkipCm);
            
            return initBoundsSol;
        }
        return null;
    }

    private void printSentences(DepTreebank depTreebank)
            throws IOException {
        SentenceCollection sentences = depTreebank.getSentences();
        if (printSentences != null) {
            BufferedWriter writer = new BufferedWriter(new FileWriter(printSentences));
            // TODO: improve this
            log.info("Printing sentences...");
            writer.write("Sentences:\n");
            for (Sentence sent : sentences) {
                StringBuilder sb = new StringBuilder();
                for (Label label : sent) {
                    if (label instanceof TaggedWord) {
                        sb.append(((TaggedWord)label).getWord());
                    } else {
                        sb.append(label.getLabel());
                    }
                    sb.append(" ");
                }
                sb.append("\t");
                for (Label label : sent) {
                    if (label instanceof TaggedWord) {
                        sb.append(((TaggedWord)label).getTag());
                        sb.append(" ");
                    }
                }
                sb.append("\n");
                writer.write(sb.toString());
            }
            if (synthetic != null) {
                log.info("Printing trees...");
                // Also print the synthetic trees
                writer.write("Trees:\n");
                writer.write(depTreebank.toString());
            }
            writer.close();
        }
    }
    
    private static void configureLogging() {
        //ConsoleAppender cAppender = new ConsoleAppender(new EnhancedPatternLayout("%d{HH:mm:ss,SSS} [%t] %p %c %x - %m%n"));
        //BasicConfigurator.configure(cAppender); 
    }
    
    public static void main(String[] args) throws IOException {
        configureLogging();
        
        ArgParser parser = new ArgParser(PipelineRunner.class);
        parser.addClass(PipelineRunner.class);
        parser.addClass(DepTreebankReader.class);
        parser.addClass(TrainerFactory.class);
        try {
            parser.parseArgs(args);
        } catch (ParseException e) {
            log.error(e.getMessage());
            parser.printUsage();
            System.exit(1);
        }
        
        Prng.seed(seed);
        
        PipelineRunner pipeline = new PipelineRunner();
        try {
            pipeline.run();
        } catch (ParseException e1) {
            log.error(e1.getMessage());
            parser.printUsage();
            System.exit(1);
        }
    }

}
