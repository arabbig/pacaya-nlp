#!/usr/bin/python

import sys
import os
import getopt
import math
import tempfile
import stat
import shlex
import subprocess
from subprocess import Popen
from optparse import OptionParser
from experiments.core.util import get_new_file, sweep_mult, fancify_cmd, frange
from experiments.core.util import head_sentences
import platform
from glob import glob
from experiments.core.experiment_runner import ExpParamsRunner, get_subset
from experiments.core import experiment_runner
from experiments.core import pipeline
import re
import random
from experiments.core.pipeline import write_script, RootStage, Stage
from experiments import run_srl

def get_root_dir():
    scripts_dir =  os.path.abspath(sys.path[0])
    root_dir =  os.path.dirname(os.path.dirname(scripts_dir))
    print "Using root_dir: " + root_dir
    return root_dir;

def get_dev_data(data_dir, file_prefix, name=None, data_type=None):
    return get_some_data(data_dir, file_prefix, name, "dev", data_type)

def get_test_data(data_dir, file_prefix, name=None, data_type=None):
    return get_some_data(data_dir, file_prefix, name, "test", data_type)

def get_some_data(data_dir, file_prefix, name, test_suffix, data_type): 
    data = DPExpParams()
    if name == None:
        name = file_prefix.replace("/", "-")
    if data_type == None:
        data_type = "PTB"
    data.set("dataset", name,True,False)
    data.set("train","%s/%s" % (data_dir, file_prefix),False,True)
    data.update(trainType=data_type)
    #data.set("dev","%s/%s.%s" % (data_dir, file_prefix, test_suffix),False,True)
    return data

class ScrapeDP(experiment_runner.PythonExpParams):
    
    def __init__(self, **keywords):
        experiment_runner.PythonExpParams.__init__(self,keywords)
        self.always_relaunch()

    def get_initial_keys(self):
        return "dataSet model k s".split()
    
    def get_instance(self):
        return ScrapeDP()
    
    def get_name(self):
        return "scrape_depparse"
    
    def create_experiment_script(self, exp_dir):
        self.add_arg(os.path.dirname(exp_dir))
        script = ""
        script += "export PYTHONPATH=%s/scripts:$PYTHONPATH\n" % (self.root_dir)
        cmd = "python %s/scripts/experiments/scrape_depparse.py %s\n" % (self.root_dir, self.get_args())
        script += fancify_cmd(cmd)
        return script

class ScrapeStatuses(experiment_runner.PythonExpParams):
    
    def __init__(self, stages_to_scrape, **keywords):
        experiment_runner.PythonExpParams.__init__(self,keywords)
        self.always_relaunch()
        self.stages_to_scrape = stages_to_scrape
        
    def get_initial_keys(self):
        return "dataSet model k s".split()
    
    def get_instance(self):
        return ScrapeStatuses()
    
    def get_name(self):
        return "scrape_statuses_" + str(self.get("type"))
    
    def create_experiment_script(self, exp_dir):
        # Add directories to scrape.
        for stage in self.stages_to_scrape:
            # TODO: Debug this: with hproj=cpu enabled, all the stage.cwd fields are None.
            self.add_arg(stage.cwd)
        script = ""
        script += "export PYTHONPATH=%s/scripts:$PYTHONPATH\n" % (self.root_dir)
        cmd = "python %s/scripts/experiments/scrape_statuses.py %s\n" % (self.root_dir, self.get_args())
        script += fancify_cmd(cmd)
        return script

class SvnCommitResults(pipeline.NamedStage):
    '''TODO: Move this to core.'''
    def __init__(self, expname):
        pipeline.NamedStage.__init__(self, "svn_commit_results")
        self.always_relaunch()
        self.expname = expname
        self.minutes = 10
        
    def get_instance(self):
        return SvnCommitResults()
        
    def create_stage_script(self, exp_dir):
        # TODO: check that all the experiments completed successfully 
        # before committing. 
        top_dir = os.path.dirname(exp_dir)
        results_exp_dir = "%s/results/%s" % (self.root_dir, self.expname)
        # Copy results files ending in .data or .csv to results/<expname>
        script = ""
        script += "mkdir %s\n" % (results_exp_dir)
        script += "find %s | grep -P '.data$|.csv$' "  % (top_dir)
        # We use -I and -n with xargs because cp's -t is not supported on Mac OS X.
        script += " | xargs -I %% -n 1 cp %% %s/ \n" % (results_exp_dir)
        # Add all new results to svn
        script += "svn add --force %s/results\n" % (self.root_dir)
        # Commit the new results to svn
        script += "svn commit -m 'AUTOCOMMIT: Updates to results "
        script += " from %s' %s/results\n" % (os.path.basename(top_dir), self.root_dir)
        return script

class DPExpParams(experiment_runner.JavaExpParams):
    
    def __init__(self, **keywords):
        experiment_runner.JavaExpParams.__init__(self,keywords)
            
    def get_initial_keys(self):
        return "dataSet model k s".split()
    
    def get_instance(self):
        return DPExpParams()
    
    def create_experiment_script(self, exp_dir):
        script = ""
        script += "echo 'CLASSPATH=$CLASSPATH'\n"
        cmd = "java " + self.get_java_args() + " edu.jhu.train.dmv.DepParserRunner  %s \n" % (self.get_args())
        script += fancify_cmd(cmd)
        return script
    
    def get_java_args(self):
        # Allot the available memory to the JVM, ILP solver, and ZIMPL
        total_work_mem_megs = self.work_mem_megs
        if (self.get("parser").startswith("ilp-")):
            zimpl_mem = int(total_work_mem_megs * 0.5)
        else:
            zimpl_mem = 0
        java_mem = int((total_work_mem_megs - zimpl_mem) * 0.5)
        ilp_mem = total_work_mem_megs - java_mem - zimpl_mem
        # Subtract off some overhead for CPLEX
        ilp_mem -= 1024
        assert (zimpl_mem + java_mem + ilp_mem <= total_work_mem_megs)

        self.update(ilpWorkMemMegs=ilp_mem)
        
        # Create the JVM args
        java_args = self._get_java_args(java_mem)  
        if True: 
            # HACK: revert back to this if-clause after adding real parser for eval: self.get("ilpSolver") == "cplex":  
            mac_jlp = "/Users/mgormley/installed/IBM/ILOG/CPLEX_Studio125/cplex/bin/x86-64_darwin"
            coe_jlp = "/home/hltcoe/mgormley/installed/IBM/ILOG/CPLEX_Studio125/cplex/bin/x86-64_sles10_4.1"
            if os.path.exists(mac_jlp): jlp = mac_jlp
            elif os.path.exists(coe_jlp): jlp = coe_jlp
            else: raise Exception("Could not find java.library.path for CPLEX")
            java_args += " -Djava.library.path=%s " % (jlp)
        return java_args

class CkyExpParams(experiment_runner.JavaExpParams):
    
    def __init__(self, **keywords):
        experiment_runner.JavaExpParams.__init__(self,keywords)
            
    def get_initial_keys(self):
        return "dataSet model k s".split()
    
    def get_instance(self):
        return CkyExpParams()
    
    def create_experiment_script(self, exp_dir):
        script = ""
        script += "echo 'CLASSPATH=$CLASSPATH'\n"
        cmd = "java " + self.get_java_args() + " edu.jhu.parse.cky.RunCkyParser  %s \n" % (self.get_args())
        script += fancify_cmd(cmd)
        return script
    

class HProfCpuExpParams(DPExpParams):

    def __init__(self):
        DPExpParams.__init__(self)
        self.set("hprof","cpu-samples",True,False)
        
    def get_instance(self):
        return HProfCpuExpParams()
    
    def get_java_args(self):
        # Default interval is 10ms
        return DPExpParams.get_java_args(self) + " -agentlib:hprof=cpu=samples,depth=7,interval=2 "
    
class HProfHeapExpParams(DPExpParams):

    def __init__(self):
        DPExpParams.__init__(self)
        self.set("hprof","heap-sites",True,False)
        
    def get_instance(self):
        return HProfHeapExpParams()
    
    def get_java_args(self):
        return DPExpParams.get_java_args(self) + " -agentlib:hprof=heap=sites,depth=7 "
    

class DepParseExpParamsRunner(ExpParamsRunner):
    
    def __init__(self, options):
        ExpParamsRunner.__init__(self, options.expname, options.queue, print_to_console=True, dry_run=options.dry_run)
        self.root_dir = os.path.abspath(get_root_dir())
        self.fast = options.fast
        self.expname = options.expname
        self.data = options.data
        self.hprof = options.hprof
        if options.test:
            self.get_data = get_test_data
        else:
            self.get_data = get_dev_data
            
        if self.queue and not self.queue == "mem":
            print "WARN: Are you sure you don't want the mem queue?"
            
    def get_experiments(self):
        all = DPExpParams()
        all.set("expname", self.expname, False, False)
        all.set("timeoutSeconds", 8*60*60, incl_arg=False, incl_name=False)
        all.set("work_mem_megs", 1.5*1024, incl_arg=False, incl_name=False)
        all.remove("work_mem_megs")
        all.update(formulation="FLOW_PROJ_LPRELAX_FCOBJ",
                   parser="cky",
                   model="dmv",
                   algorithm="bnb",
                   ilpSolver="cplex",
                   iterations=1000, 
                   convergenceRatio=0.99999,
                   epsilon=0.1,
                   varSelection="regret",
                   varSplit="half-prob",
                   maxSimplexIterations=1000000000,
                   maxDwIterations=1000000000, 
                   maxSetSizeToConstrain=0, 
                   initWeights="uniform",
                   nodeOrder="plunging-bfs",
                   seed=random.getrandbits(63),
                   propSupervised=0.0,
                   threads=1,
                   numRestarts=10,
                   initSolNumRestarts=10,
                   projType="UNBOUNDED_MIN_EUCLIDEAN", # This affects more than just the projection algorithm.
                   drRenormalize=True,
                   drConversion="SEPARATE_EQ_AND_LEQ",
                   drAlpha=0.1,
                   drUseIdentityMatrix=False, 
                   inclExtraParseCons=False,
                   simplexAlgorithm="DUAL", # BARRIER is inexplicably slow (135s) on 500 sentences vs. DUAL (3s).
                   rootMaxCutRounds=1,
                   maxCutRounds=1,
                   minSumForCuts=1.00001,
                   maxStoCuts=1000,
                   printModel="./model.txt",
                   bnbTimeoutSeconds=100,
                   universalPostCons=False,
                   addBindingCons=False,
                   relaxOnly=False)
        all.set("lambda", 1.0)
                
        # Keys to exclude from the args.
        all.set_incl_arg("dataset", False)
        # Keys to exclude from the name.
        all.set_incl_name("train", False)
        all.set_incl_name("test", False)
        all.set_incl_name("brownClusters", False)
        
        dgFixedInterval = DPExpParams(deltaGenerator="fixed-interval",interval=0.01,numPerSide=2)
        dgFactor = DPExpParams(deltaGenerator="factor",factor=1.1,numPerSide=2)
        
        # Define commonly used relaxations:
        dwRelax = DPExpParams(relaxation="dw",
                              envelopeOnly=True)
        lpRelax = DPExpParams(relaxation="rlt",
                              envelopeOnly=True,
                              rltFilter="obj-var")
        rltObjVarRelax = DPExpParams(relaxation="rlt",
                                     envelopeOnly=False,
                                     rltFilter="obj-var")
        rltAllRelax = DPExpParams(relaxation="rlt",
                                  envelopeOnly=False,
                                  rltFilter="prop",
                                  rltInitProp=1.0,
                                  rltCutProp=1.0)
        universalPostCons = DPExpParams(universalPostCons=True,
                                        universalMinProp=0.75,
                                        universalMinPropAddend=0.05)
        default_relax = lpRelax
                
        # Define commonly used projections:
        norm_proj = DPExpParams(projAlgo="BASIC", projType="NORMALIZE")
        euclid_proj = DPExpParams(projAlgo="BASIC", projType="UNBOUNDED_MIN_EUCLIDEAN")
        tree_proj = DPExpParams(projAlgo="VEM", vemProjPropImproveModel=0.0, vemProjPropImproveTreebank=1.0)
        model_proj = DPExpParams(projAlgo="VEM", vemProjPropImproveModel=1.0, vemProjPropImproveTreebank=0.0)
        all_proj = DPExpParams(projAlgo="VEM", vemProjPropImproveModel=0.5, vemProjPropImproveTreebank=0.5)
        # Set default projection.    
        all = all + all_proj
        
        # PTB Data.
        data_dir = os.path.join(self.root_dir, "data")
        wsj_02 = self.get_data(data_dir, "treebank_3_sym/wsj/02", "PTB")
        wsj_22 = self.get_data(data_dir, "treebank_3/wsj/22", "PTB")
        wsj_23 = self.get_data(data_dir, "treebank_3/wsj/23", "PTB")
        wsj_24 = self.get_data(data_dir, "treebank_3/wsj/24", "PTB")
        wsj_full = self.get_data(data_dir, "treebank_3_sym/wsj", "PTB") # Only sections 2-21
        brown_cf = self.get_data(data_dir, "treebank_3/brown/cf", "PTB")
        brown_full = self.get_data(data_dir, "treebank_3/brown", "PTB")
        
        # CoNLL-2009 Data
        l = run_srl.ParamGroupLists()
        g = run_srl.ParamGroups()      
        p = run_srl.PathDefinitions(options).get_paths() 
        g.langs = {}
        for lang_short in p.lang_short_names:
            g.langs[lang_short] = run_srl.ParamGroups()            
        for lang_short in p.lang_short_names:
            setup = DPExpParams(
                        trainType="CONLL_2009",
                        testType="CONLL_2009",
                        trainOut="train-parses.txt",
                        testOut="test-parses.txt")
            pl = p.langs[lang_short]
            gl = g.langs[lang_short]
            gl.conll09_train = setup + DPExpParams(dataset="conll09-%s-train" % (lang_short),
                                                               train=pl.pos_gold_train,
                                                               test=pl.pos_gold_train)            
            gl.conll09_dev   = setup + DPExpParams(dataset="conll09-%s-dev" % (lang_short),
                                                               train=pl.pos_gold_train,
                                                               test=pl.pos_gold_dev)          
            gl.conll09_eval  = setup + DPExpParams(dataset="conll09-%s-eval" % (lang_short),
                                                               train=pl.pos_gold_train,
                                                               test=pl.pos_gold_eval)
                
        # Synthetic Data.
        synth_alt_three = DPExpParams(synthetic="alt-three", trainType="SYNTHETIC")
        synth_alt_three.set("dataset", "alt-three", True, False)

        wsj = wsj_full if not self.fast else wsj_02
        brown = brown_full if not self.fast else brown_cf
        
        # Reducing tagset explicitly
        for ptbdata in [wsj_02, wsj_full, brown_cf, brown_full]:
            #ptbdata.update(reduceTags="%s/data/universal_pos_tags.1.02/en-ptb.map" % (self.root_dir))
            ptbdata.update(reduceTags="%s/data/tag_maps/en-ptb-plus-aux.map" % (self.root_dir))
        
        # Printing synthetic data with fixed synthetic seed.
        for synthdata in [synth_alt_three]: 
            synthdata.update(printSentences="./data.txt",
                             syntheticSeed=123454321)
        
        if self.data == "synthetic": datasets = [synth_alt_three]
        elif self.fast:       datasets = [brown_cf]
        else:               datasets = [brown_full]
        
        # Default datasets.
        default_brown = brown + DPExpParams(maxSentenceLength=10, 
                                            maxNumSentences=200,
                                            dataset="brown200")
        default_wsj = wsj_02 + DPExpParams(maxSentenceLength=10, 
                                            maxNumSentences=200,
                                            dataset="wsj200")
        
        ## Only keeping sentences that contain a verb
        #default_brown.update(mustContainVerb=True)
        #default_wsj.update(mustContainVerb=True)
        
        brown100 = default_brown + DPExpParams(maxNumSentences=100, dataset="brown100")
        default_synth = synth_alt_three + DPExpParams(maxNumSentences=5)
        
        experiments = []
        if self.expname == "viterbi-em":
            root = RootStage()
            setup = default_wsj
            setup.update(algorithm="viterbi", parser="cky", numRestarts=0, iterations=1000, convergenceRatio=0.99999)
            setup.set("lambda", 1)
            for initWeights in ["uniform", "random"]:
                setup.update(initWeights=initWeights)
                for randomRestartId in range(100):
                    setup.set("randomRestartId", randomRestartId, True, False)
                    # Set the seed explicitly.
                    experiment = all + setup + DPExpParams(seed=random.getrandbits(63))
                    root.add_dependent(experiment + universalPostCons + DPExpParams(parser="relaxed"))
                    #root.add_dependent(experiment + universalPostCons)
                    root.add_dependent(experiment)
            scrape = ScrapeDP(tsv_file="results.data")
            scrape.add_prereqs(root.dependents)
            svnco = SvnCommitResults(self.expname)
            svnco.add_prereq(scrape)
            return root
        
        elif self.expname == "vem-wsj":
            root = RootStage()
            setup = wsj
            setup.update(maxNumSentences=100000000)
            setup.update(algorithm="viterbi", parser="cky", numRestarts=0, iterations=1000, convergenceRatio=0.99999)
            setup.set("lambda", 1)
            for maxSentenceLength in [10, 20]:
                setup.update(maxSentenceLength=maxSentenceLength)
                for initWeights in ["uniform", "random"]:
                    setup.update(initWeights=initWeights)
                    for randomRestartId in range(1):
                        setup.set("randomRestartId", randomRestartId, True, False)
                        # Set the seed explicitly.
                        experiment = all + setup + DPExpParams(seed=random.getrandbits(63))
                        root.add_dependent(experiment + universalPostCons + DPExpParams(parser="relaxed"))
                        root.add_dependent(experiment)
            scrape = ScrapeDP(tsv_file="results.data")
            scrape.add_prereqs(root.dependents)
            svnco = SvnCommitResults(self.expname)
            svnco.add_prereq(scrape)
            return root
        
        elif self.expname == "vem-conll":
            root = RootStage()
            setup = DPExpParams()
            # All train sentences and full-length test sentences.
            setup.update(maxNumSentences=100000000, maxSentenceLengthTest=100000000)
            setup.update(algorithm="viterbi", parser="cky", numRestarts=10, iterations=1000, convergenceRatio=0.99999)
            setup.update(usePredictedPosTags=True, modelOut="model.binary.gz")
            setup.update(timeoutSeconds=48*60*60) # if maxSentenceLength > 20: ??
            setup.set("lambda", 1)
            exps = []
            for maxSentenceLength in [10, 20, 30]: # Dropped +inf (i.e. 1000)                    
                setup.update(maxSentenceLength=maxSentenceLength)
                for lang_short in p.lang_short_names:
                    pl = p.langs[lang_short]
                    gl = g.langs[lang_short]
                    for dataset in [gl.conll09_train, gl.conll09_dev, gl.conll09_eval]:
                        for brownClusters in [None, pl.bc_256]:                            
                            if brownClusters is not None:
                                bc = DPExpParams(brownClusters=brownClusters, dataset=dataset.get("dataset") + "-brown")
                            else:
                                bc = DPExpParams()
                            for usePredArgSupervision in [True, False]:
                                # Set the seed explicitly.
                                exp = all + setup + dataset + bc + DPExpParams(usePredArgSupervision=usePredArgSupervision)
                                if dataset == gl.conll09_train:
                                    setup.set("work_mem_megs", 64*1024, False, False)
                                else:
                                    setup.remove("work_mem_megs")
                                #root.add_dependent(exp + universalPostCons + DPExpParams(parser="relaxed"))
                                exps.append(exp)
            # Drop all but 3 experiments for a fast run.
            if self.fast: exps = exps[:4]
            root.add_dependents(exps)
            scrape = ScrapeDP(tsv_file="results.data")
            scrape.add_prereqs(root.dependents)
            svnco = SvnCommitResults(self.expname)
            svnco.add_prereq(scrape)
            return root
        
        elif self.expname == "parse-wsj":
            root = RootStage()
            setup = CkyExpParams(grammar="%s/data/grammars/eng.R0.gr.gz" % (get_root_dir()),
                                 evalbDir="%s/../other_lib/EVALB" % (get_root_dir()),
                                 treeFile="trees.mrg",
                                 parseFile="parses.mrg",
                                 #maxNumSentences=10,
                                 #maxSentenceLength=10,
                                 )
            grR0=CkyExpParams()
            grR0.set("grammar", "%s/data/grammars/eng.R0.gr.gz" % (get_root_dir()), False, True)
            grR0.set("grammarName", "eng.R0.gr.gz", True, False)
            grsm6=CkyExpParams()
            grsm6.set("grammar", "%s/data/grammars/eng.sm6.gr.gz" % (get_root_dir()), False, True)
            grsm6.set("grammarName", "eng.sm6.gr.gz", True, False)

            grammars = [grR0, grsm6]
            for dataset in [wsj_02, wsj_23, wsj_24]:
                for grammar in grammars:
                    # Set the seed explicitly.
                    experiment = dataset + setup + grammar
                    experiment.remove("reduceTags")
                    root.add_dependent(experiment)
            return root
        
        elif self.expname == "viterbi-vs-bnb":
            root = RootStage()
            all.update(algorithm="bnb")
            # Run for some fixed amount of time.                
            all.update(numRestarts        = 1000000000,
                       initSolNumRestarts = 1000000000,
                       timeoutSeconds        = 8*60*60,
                       initSolTimeoutSeconds = 45*60)
            
            rltAllRelax.update(rltFilter="max")
            maxes = [1000, 10000, 100000]
            extra_relaxes = [rltAllRelax + DPExpParams(rltInitMax=p, rltCutMax=p) for p in maxes]
            extra_relaxes += [x + DPExpParams(rltCutMax=0) for x in extra_relaxes]
            exps = []
            for dataset in [default_wsj, default_brown]:
                for algorithm in ["viterbi", "bnb"]:
                    experiment = all + dataset + DPExpParams(algorithm=algorithm)
                    if algorithm == "viterbi":
                        exps.append(experiment + universalPostCons + DPExpParams(parser="relaxed"))
                        exps.append(experiment)
                        #exps.append(experiment + universalPostCons) # parser="cky"
                    else:
                        for relax in [lpRelax, rltObjVarRelax] + extra_relaxes:
                            exps.append(experiment + relax)
                            #exps.append(experiment + relax + universalPostCons) # parser="cky"
                            exps.append(experiment + relax + universalPostCons + DPExpParams(parser="relaxed"))
            if self.fast:
                # Drop all but 3 experiments for a fast run.
                exps = exps[:4]
            root.add_dependents(exps)
            # Scrape all results.
            scrape = ScrapeDP(tsv_file="results.data", csv_file="results.csv")
            scrape.add_prereqs(root.dependents)
            #Scrape status information from a subset of the experiments.
            scrape_stat = ScrapeStatuses(root.dependents, tsv_file="bnb-status.data", type="bnb")
            scrape_stat.add_prereqs(root.dependents)
            scrape_stat = ScrapeStatuses(root.dependents, tsv_file="incumbent-status.data", type="incumbent")
            scrape_stat.add_prereqs(root.dependents)
            if not self.fast:
                # Commit results to svn
                svnco = SvnCommitResults(self.expname)
                svnco.add_prereqs([scrape, scrape_stat])
            return root
        
        elif self.expname == "bnb":
            root = RootStage()
            all.update(algorithm="bnb", epsilon=0.01)
            # Run for some fixed amount of time.                
            all.update(numRestarts=1000000000)
            all.update(timeoutSeconds=10*60)
            rltAllRelax.update(rltFilter="max")
            maxes = [1000, 10000, 100000]
            #maxes = [1000, 10000]
            extra_relaxes = [rltAllRelax + DPExpParams(rltInitMax=p, rltCutMax=p) for p in maxes]
            extra_relaxes += [x + DPExpParams(rltCutMax=0) for x in extra_relaxes]
            exps = []
            for dataset in [default_synth]:
                for relax in [lpRelax, rltObjVarRelax] + extra_relaxes:
                    experiment = all + dataset + relax
                    exps.append(experiment)
            if self.fast:
                # Drop all but 3 experiments for a fast run.
                exps = exps[:3]
            root.add_dependents(exps)
            # Scrape all results.
            scrape = ScrapeDP(tsv_file="results.data", csv_file="results.csv")
            scrape.add_prereqs(root.dependents)
            #Scrape status information from a subset of the experiments.
            scrape_stat = ScrapeStatuses(root.dependents, tsv_file="bnb-status.data", type="bnb")
            scrape_stat.add_prereqs(root.dependents)
            if not self.fast:
                # Commit results to svn
                svnco = SvnCommitResults(self.expname)
                svnco.add_prereqs([scrape, scrape_stat])
            return root
        
        elif self.expname == "bnb-semi":
            root = RootStage()
            all.update(algorithm="bnb",
                       initBounds="VITERBI_EM")
            dataset = brown
            for maxSentenceLength, maxNumSentences, timeoutSeconds in [(5, 100, 1*60*60), (10, 300, 1*60*60)]:
                msl = DPExpParams(maxSentenceLength=maxSentenceLength)
                mns = DPExpParams(maxNumSentences=maxNumSentences)
                if not self.fast:
                    # Run for some fixed amount of time.                
                    all.update(numRestarts=1000000000)
                    all.update(timeoutSeconds=timeoutSeconds)
                for varSplit in ["half-prob", "half-logprob"]:
                    for offsetProb in [0.05, 0.1, 0.2, 0.5, 1.0]: #TODO: frange(10e-13, 0.21,0.05):
                        for propSupervised in frange(0.0, 1.0, 0.1):
                            algo = DPExpParams(varSplit=varSplit, offsetProb=offsetProb, 
                                               propSupervised=propSupervised)
                            experiment = all + dataset + msl + mns + algo
                            root.add_dependent(experiment)
            # Scrape all results.
            scrape = ScrapeDP(tsv_file="results.data")
            scrape.add_prereqs(root.dependents)
            #Scrape status information from a subset of the experiments.
            subset = get_subset(root.dependents, offsetProb=1.0, maxSentenceLength=10, maxNumSentences=300)
            scrape_stat = ScrapeStatuses(subset, tsv_file="bnb-status.data", type="bnb")
            scrape_stat.add_prereqs(subset)
            # Commit results to svn
            svnco = SvnCommitResults(self.expname)
            svnco.add_prereqs([scrape, scrape_stat])
            return root
        
        elif self.expname == "bnb-semi-synth":
            all.update(algorithm="bnb",
                       initBounds="GOLD",
                       initWeights="gold",
                       varSelection="regret")
            dataset = synth_alt_three
            for maxNumSentences, timeoutSeconds in [(300, 20*60)]:
                mns = DPExpParams(maxNumSentences=maxNumSentences)
                if not self.fast:
                    # Run for some fixed amount of time.                
                    all.update(numRestarts=1000000000)
                    all.update(timeoutSeconds=timeoutSeconds)
                for varSplit in ["half-prob", "half-logprob"]:
                    for varSelection in ["regret", "pseudocost", "full"]:
                        for offsetProb in [0.05, 0.1, 0.2, 0.5, 1.0]:
                            for propSupervised in frange(0.0, 1.0, 0.1):
                                algo = DPExpParams(varSplit=varSplit, offsetProb=offsetProb, 
                                                   propSupervised=propSupervised, varSelection=varSelection)
                                experiments.append(all + dataset + mns + algo)
                                
        elif self.expname == "bnb-depth-test":
            root = RootStage()
            all.update(algorithm="bnb-rand-walk",
                       disableFathoming=False,
                       nodeOrder="dfs-randwalk",
                       maxRandWalkSamples=10000)
            # Run for some fixed amount of time.
            all.update(numRestarts=1000000000,
                       timeoutSeconds=8*60*60)
            rltAllRelax.update(rltFilter="max")
            maxes = [1000, 10000, 100000]
            extra_relaxes = [rltAllRelax + DPExpParams(rltInitMax=p, rltCutMax=p) for p in maxes]
            extra_relaxes += [rltAllRelax + DPExpParams(rltInitMax=p, rltCutMax=0) for p in maxes]
            extra_relaxes += [rltAllRelax + DPExpParams(rltInitMax=p, rltCutMax=p, addBindingCons=True) for p in maxes]
            exps = []
            for dataset in [default_synth, default_brown]:
                # Add default case:
                exps.append(all + dataset + default_relax)
                # Add special cases:
                # TODO: This is really HACKY.
                for varSplit in ["half-logprob"]:#["half-prob", "half-logprob"]:
                    exps.append(all + dataset + default_relax + DPExpParams(varSplit=varSplit))
                for varSelection in ["full"]:#["regret", "pseudocost", "full"]:
                    exps.append(all + dataset + default_relax +  DPExpParams(varSelection=varSelection))
                for relax in [rltObjVarRelax] + extra_relaxes: #[lpRelax, rltObjVarRelax] + extra_relaxes:
                    experiment = all + dataset + relax                        
                    exps.append(experiment)
                # TODO: Testing projections in B&B depth test is a bit odd...
                for proj in [norm_proj, euclid_proj, tree_proj, model_proj]: #[norm_proj, euclid_proj, tree_proj, model_proj, all_proj]:
                    exps.append(all + dataset + default_relax + proj)
                    
            if self.fast:
                # Drop all but 3 experiments for a fast run.
                exps = exps[:3]
            root.add_dependents(exps)
            #Scrape status information from a subset of the experiments.
            scrape_stat = ScrapeStatuses(root.dependents, tsv_file="curnode-status.data", type="curnode")
            scrape_stat.add_prereqs(root.dependents)
            # Scrape all results.
            scrape = ScrapeDP(tsv_file="results.data", csv_file="results.csv")
            scrape.add_prereqs(root.dependents)
            if not self.fast:
                # Commit results to svn
                svnco = SvnCommitResults(self.expname)
                svnco.add_prereqs([scrape, scrape_stat])
            return root
        
        elif self.expname == "bnb-supervised":
            root = RootStage()
            all.update(algorithm="bnb",
                       initBounds="VITERBI_EM",                    
                       varSelection="regret",
                       offsetProb=1.0, 
                       varSplit="half-prob",
                       propSupervised=1.0,
                       maxSimplexIterations=1000000000,
                       maxDwIterations=1000000000,
                       maxCutRounds=1000000000,
                       minSumForCuts=1.00001,
                       maxSentenceLength=10,
                       maxNumSentences=300,)
            all.set("lambda", 0.0)
            # Run for some fixed amount of time.                
            all.update(numRestarts=1000000000, epsilon=0.0,
                       timeoutSeconds=2*60)
            dataset = brown
            for relaxation in ["dw", "dw-res"]:
                experiment = all + dataset + DPExpParams(relaxation=relaxation)
                root.add_dependent(experiment)
            return root
        
        elif self.expname == "bnb-hprof":
            all.update(algorithm="bnb")
            for dataset in datasets:
                for maxSentenceLength in [3,5]:
                    msl = DPExpParams(maxSentenceLength=maxSentenceLength)
                    for maxNumSentences in [10,100]:
                        mns = DPExpParams(maxNumSentences=maxNumSentences)
                        for varSelection in ["regret", "rand-uniform", "rand-weighted", "full"]:
                            experiments.append(all + dataset + msl + mns + DPExpParams(varSelection=varSelection) + HProfCpuExpParams())
                            
        elif self.expname == "bnb-expanding-boxes":
            # Fixed seed
            all.update(algorithm="bnb", seed=112233)
            for dataset in datasets:
                for maxSentenceLength, maxNumSentences, timeoutSeconds in [(5, 50, 1*60*60), (10, 300, 4*60*60)]:
                    msl = DPExpParams(maxSentenceLength=maxSentenceLength)
                    mns = DPExpParams(maxNumSentences=maxNumSentences)
                    if not self.fast:
                        # Run for some fixed amount of time.                
                        all.update(numRestarts=1000000000)
                        all.update(timeoutSeconds=timeoutSeconds)
                    for varSelection in ["regret"]:
                        for initBounds in ["VITERBI_EM"]: #TODO: , "random", "uniform"]: # TODO: "gold"
                            for offsetProb in [0.05, 0.1, 0.2, 0.5, 1.0]: #TODO: frange(10e-13, 0.21,0.05):
                                for probOfSkipCm in [0.0]: #TODO: frange(0.0, 0.21, 0.05):
                                    algo = DPExpParams(varSelection=varSelection,initBounds=initBounds,offsetProb=offsetProb, probOfSkipCm=probOfSkipCm)
                                    experiments.append(all + dataset + msl + mns + algo)
                                    
        elif self.expname == "viterbi-bnb":
            root = RootStage()
            # Fixed seed
            all.update(seed=112233)
            for dataset in datasets:
                for maxSentenceLength, maxNumSentences, timeoutSeconds in [(10, 300, 1*60*60)]:
                    msl = DPExpParams(maxSentenceLength=maxSentenceLength)
                    mns = DPExpParams(maxNumSentences=maxNumSentences)
                    if not self.fast:
                        # Run for some fixed amount of time.
                        all.update(numRestarts=1000000000)
                        all.update(timeoutSeconds=timeoutSeconds)
                    for algorithm in ["viterbi", "viterbi-bnb", "bnb"]:
                        algo = DPExpParams(algorithm=algorithm)
                        if algorithm == "viterbi-bnb":
                            if not self.fast:
                                algo.update(bnbTimeoutSeconds=maxNumSentences/3)
                            for offsetProb in frange(0.05, 0.21, 0.05):
                                algo.update(offsetProb=offsetProb)
                                root.add_dependent(all + dataset + msl + mns + algo)
                        else:
                            root.add_dependent(all + dataset + msl + mns + algo)
            # Scrape all results.
            scrape = ScrapeDP(tsv_file="results.data")
            scrape.add_prereqs(root.dependents)
            #Scrape status information from a subset of the experiments.
            #TODO: maybe a subset? #get_subset(root.dependents, offsetProb=1.0, maxSentenceLength=10, maxNumSentences=300) 
            subset = root.dependents
            scrape_stat = ScrapeStatuses(subset, tsv_file="incumbent-status.data", type="incumbent")
            scrape_stat.add_prereqs(subset)
            # Commit results to svn
            svnco = SvnCommitResults(self.expname)
            svnco.add_prereqs([scrape, scrape_stat])
            return root
        
        elif self.expname == "node-orders":
            root = RootStage()
            all.update(algorithm="bnb",
                       rootMaxCutRounds=1,
                       maxCutRounds=0,
                       minSumForCuts=1.00001,
                       maxStoCuts=1000)
            relax = rltAllRelax + DPExpParams()
            relax.update(rltFilter="max",
                         rltInitMax=10000,
                         rltCutMax=0)
            dataset = synth_alt_three + DPExpParams()
            dataset.update(maxSentenceLength=5,
                           maxNumSentences=15,
                           timeoutSeconds=15*60)
            exps = []
            for nodeOrder in ["bfs", "dfs"]:
                experiment = all + dataset + relax + DPExpParams(nodeOrder=nodeOrder)
                exps.append(experiment)
            for localRelativeGapThreshold in [0.25, 0.5, 0.75, 1.0]:
                experiment = all + dataset + relax + DPExpParams(nodeOrder="plunging-bfs", 
                                                                 localRelativeGapThreshold=localRelativeGapThreshold)
                exps.append(experiment)
            if self.fast:
                # Drop all but 3 experiments for a fast run.
                exps = exps[:3]
            root.add_dependents(exps)
            # Scrape all results.
            scrape = ScrapeDP(tsv_file="results.data", csv_file="results.csv")
            scrape.add_prereqs(root.dependents)
            #Scrape status information from a subset of the experiments.
            scrape_stat = ScrapeStatuses(root.dependents, tsv_file="bnb-status.data", type="bnb")
            scrape_stat.add_prereqs(root.dependents)
            if not self.fast:
                # Commit results to svn
                svnco = SvnCommitResults(self.expname)
                svnco.add_prereqs([scrape, scrape_stat])
            return root
        
        elif self.expname == "relax-root-rlt":
            root = RootStage()
            all.update(relaxOnly=True,
                       rootMaxCutRounds=0, #TODO: maybe push this back up to 1 after we support cutting in the projection?
                       maxCutRounds=0,
                       timeoutSeconds=2*60*60)
            relax = rltAllRelax + DPExpParams(rltFilter="prop", rltCutProp=0.0)
            dataset = default_synth
            # This is broken due to probOfSkipCm.
            #            dataset = dataset + DPExpParams(initBounds="GOLD",
            #                                            offsetProb=0.25,
            #                                            probOfSkipCm=0.75)
            exps = []
            # Uncomment to compare with dimensionality-reduced relaxations.
#            for rltInitProp in frange(0.0, 1.0, 0.1):
#                for drMaxNonZerosPerRow in [1, 2, 4, 8]:
#                    for drMaxCons in [50, 100, 200, 400]:
#                        for drSamplingDist in ["UNIFORM", "DIRICHLET", "ALL_ONES"]:
#                            extra = DPExpParams(drMaxCons=drMaxCons,
#                                                drMaxNonZerosPerRow=drMaxNonZerosPerRow,
#                                                drSamplingDist=drSamplingDist,
#                                                rltInitProp=rltInitProp)
#                            experiment = all + dataset + relax + extra
#                            exps.append(experiment)
            for rltInitProp in frange(0.0, 1.0, 0.1):
                experiment = all + dataset + relax + DPExpParams(rltInitProp=rltInitProp) + DPExpParams(maxNumSentences=20)
                exps.append(experiment)
                # Uncomment to compare with identity matrix.
                #experiment = all + dataset + relax + DPExpParams(rltInitProp=rltInitProp, drUseIdentityMatrix=True)
                #exps.append(experiment)
                # Uncomment to compare with my extra parse constraints.
                #experiment = all + dataset + relax + DPExpParams(rltInitProp=rltInitProp, inclExtraParseCons=True)
                #exps.append(experiment)
            # Uncomment to test different simplex algorithms.
            #for simplexAlgorithm in ["PRIMAL", "DUAL", "BARRIER", "NETWORK", "SIFTING"]:
            #    if simplexAlgorithm == all.get("simplexAlgorithm"): continue
            #    experiment = all + dataset + relax + DPExpParams(rltInitProp=1.0, maxCutRounds=1, simplexAlgorithm=simplexAlgorithm)
            #    exps.append(experiment)
            if self.fast:
                # Drop all but 3 experiments for a fast run.
                exps = exps[:3]
            root.add_dependents(exps)
            # Scrape all results.
            scrape = ScrapeDP(tsv_file="results.data", csv_file="results.csv")
            scrape.add_prereqs(root.dependents)
            if not self.fast:
                # Commit results to svn
                svnco = SvnCommitResults(self.expname)
                svnco.add_prereqs([scrape])
            return root
        
        elif self.expname == "relax-percent-pruned":
            for dataset in datasets:
                for maxSentenceLength in [10]:
                    msl = DPExpParams(maxSentenceLength=maxSentenceLength)
                    for maxNumSentences in [300]:
                        mns = DPExpParams(maxNumSentences=maxNumSentences)
                        experiments.append(all + dataset + msl + mns + DPExpParams(algorithm="viterbi",parser="cky"))
                        for i in range(0,100):
                            for initBounds in ["RANDOM"]:
                                for offsetProb in frange(10e-13, 1.001,0.05):
                                    experiments.append(all + dataset + msl + mns + DPExpParams(initBounds=initBounds,offsetProb=offsetProb, seed=random.getrandbits(63), relaxOnly=True))
                                    
        elif self.expname == "relax-quality":
            # Fixed seed
            all.update(seed=112233)
            for dataset in datasets:
                for maxSentenceLength in [10]:
                    msl = DPExpParams(maxSentenceLength=maxSentenceLength)
                    for maxNumSentences in [100,300]:
                        mns = DPExpParams(maxNumSentences=maxNumSentences)
                        for initBounds in ["VITERBI_EM", "RANDOM", "UNIFORM"]: # TODO: "gold"
                            for offsetProb in frange(10e-13, 1.001,0.05):
                                for probOfSkipCm in frange(0.0, 0.2, 0.05):
                                    experiments.append(all + dataset + msl + mns + DPExpParams(initBounds=initBounds,offsetProb=offsetProb,probOfSkipCm=probOfSkipCm, relaxOnly=True))
                                    
        elif self.expname == "relax-compare":
            # Fixed seed
            all.update(relaxOnly=True, seed=112233)
            for dataset in datasets:
                for maxSentenceLength in [10]:
                    msl = DPExpParams(maxSentenceLength=maxSentenceLength)
                    for maxNumSentences in [300]:
                        mns = DPExpParams(maxNumSentences=maxNumSentences)
                        for initBounds in ["RANDOM"]: # TODO: "gold"
                            for offsetProb in frange(10e-13, 1.001,0.2):
                                #for probOfSkipCm in frange(0.0, 0.2, 0.05):
                                for relaxation in ["dw", "dw-res"]:
                                    for maxDwIterations in [1,2,10,100]:
                                        for maxSimplexIterations in [10,100,1000]:
                                            for maxSetSizeToConstrain in [0,2,3]:
                                                for minSumForCuts in [1.001, 1.01, 1.1]:
                                                    for maxCutRounds in [1,10,100]:
                                                        p1 = DPExpParams(initBounds=initBounds,offsetProb=offsetProb)
                                                        #p1.update(probOfSkipCm=probOfSkipCm)
                                                        p1.update(relaxation=relaxation, maxDwIterations=maxDwIterations, maxSimplexIterations=maxSimplexIterations)
                                                        p1.update(maxSetSizeToConstrain=maxSetSizeToConstrain, minSumForCuts=minSumForCuts, maxCutRounds=maxCutRounds)
                                                        if (relaxation != "dw" and maxSetSizeToConstrain > 0 and minSumForCuts > 1.001 and maxCutRounds > 1):
                                                            pass
                                                        else:
                                                            experiments.append(all + dataset + msl + mns + p1)
                                                            
        elif self.expname == "formulations":
            all.update(parser="ilp-corpus")
            for dataset in datasets:
                formulations = ["deptree-dp-proj", "deptree-explicit-proj", "deptree-flow-nonproj", "deptree-flow-proj", "deptree-multiflow-nonproj", "deptree-multiflow-proj" ]
                for formulation in formulations:
                    ilpform = DPExpParams(formulation=formulation)
                    experiments.append(all + dataset + ilpform)
                    
        elif self.expname == "corpus-size":
            # For ilp-corpus testing:
            #  all.update(iterations=1)
            all.set("lambda",0.0)
            # For SIMPLEX testing
            #  all.update(formulation="deptree-flow-nonproj-lprelax")
            for parser in ["cky"]: #["ilp-corpus","ilp-sentence"]:
                par = DPExpParams(parser=parser)
                for dataset in datasets:
                    for maxSentenceLength in [7,10,20]:
                        msl = DPExpParams(maxSentenceLength=maxSentenceLength)
                        # Limiting to max  7 words/sent there are 2394 sentences
                        # Limiting to max 10 words/sent there are 5040 sentences
                        # Limiting to max 20 words/sent there are 20570 sentences
                        if (maxSentenceLength == 7):
                            mns_list = range(200,2200,200)
                        elif (maxSentenceLength == 10):
                            mns_list = range(100,5040,100)
                        else: # for 20
                            mns_list = range(1000,20570,1000)
                        for maxNumSentences in mns_list:
                            mns = DPExpParams(maxNumSentences=maxNumSentences)
                            experiments.append(all + dataset + msl + par + mns)
                            
        elif self.expname == "deltas":
            for dataset in datasets:
                for maxSentenceLength in [5,7]:
                    msl = DPExpParams(maxSentenceLength=maxSentenceLength)
#                    if (maxSentenceLength == 5):
#                        mns_list = range(100,1500,100)
#                    elif (maxSentenceLength == 7):
#                        mns_list = range(100,2200,100)
                    mns_list = [2,4,8,16,32,64,128,256,512,1024]
                    for maxNumSentences in mns_list:
                        mns = DPExpParams(maxNumSentences=maxNumSentences)
                        # It doesn't make sense to do D-W for ilp-corpus, because there are no coupling constraints
                        experiments.append(all + dataset + msl + mns + DPExpParams(parser="ilp-corpus"))
                        for dataGen in [dgFixedInterval, dgFactor]:
                            for ilpSolver in ["cplex","dip-milpblock-cpm","dip-milpblock-pc"]:
                                experiments.append(all + dataset + msl + mns + DPExpParams(parser="ilp-deltas", ilpSolver=ilpSolver) + dataGen)
                                if ilpSolver == "cplex":
                                    experiments.append(all + dataset + msl + mns + DPExpParams(parser="ilp-deltas-init", ilpSolver=ilpSolver) + dataGen)

        else:
            raise Exception("Unknown expname: " + str(self.expname))
                
        print "Number of experiments:",len(experiments)
        print "Number of experiments: %d" % (len(experiments))
        root_stage = RootStage()
        root_stage.add_dependents(experiments)
        return root_stage

    def updateStagesForQsub(self, root_stage):
        '''Makes sure that the stage object specifies reasonable values for the 
        qsub parameters given its experimental parameters.
        '''
        for stage in self.get_stages_as_list(root_stage):
            # First make sure that the "fast" setting is actually fast.
            if isinstance(stage, DPExpParams) and self.fast:
                stage.update(iterations=1,
                             maxSentenceLength=7,
                             maxNumSentences=3,
                             numRestarts=1,
                             timeoutSeconds=6,   
                             initSolTimeoutSeconds=2,
                             bnbTimeoutSeconds=2)
            if isinstance(stage, CkyExpParams) and self.fast:
                stage.update(maxSentenceLength=7,
                             maxNumSentences=3)
            if isinstance(stage, experiment_runner.ExpParams):
                # Update the thread count
                threads = stage.get("threads")
                if threads != None: 
                    # Add an extra thread just as a precaution.
                    stage.threads = threads + 1
                work_mem_megs = stage.get("work_mem_megs")
                if work_mem_megs != None:
                    stage.work_mem_megs = work_mem_megs
                # Update the runtime
                timeoutSeconds = stage.get("timeoutSeconds")
                if timeoutSeconds != None:
                    stage.minutes = (timeoutSeconds / 60.0)
                    # Add some extra time in case some other part of the experiment
                    # (e.g. evaluation) takes excessively long.
                    stage.minutes = (stage.minutes * 2.0) + 10
            if self.hprof:
                if isinstance(stage, experiment_runner.JavaExpParams):
                    stage.hprof = self.hprof
        return root_stage

if __name__ == "__main__":
    usage = "%prog "

    parser = OptionParser(usage=usage)
    parser.add_option('-q', '--queue', help="Which SGE queue to use")
    parser.add_option('-f', '--fast', action="store_true", help="Run a fast version")
    parser.add_option('-n', '--dry_run',  action="store_true", help="Whether to just do a dry run.")
    parser.add_option('--test', action="store_true", help="Use test data")
    parser.add_option('-e', '--expname',  help="Experiment name")
    parser.add_option('--data',  help="Dataset to use")
    parser.add_option('--hprof',  help="What type of profiling to use [cpu, heap]")
    (options, args) = parser.parse_args(sys.argv)

    if len(args) != 1:
        parser.print_help()
        sys.exit(1)
    
    runner = DepParseExpParamsRunner(options)
    root_stage = runner.get_experiments()
    root_stage = runner.updateStagesForQsub(root_stage)
    runner.run_pipeline(root_stage)


