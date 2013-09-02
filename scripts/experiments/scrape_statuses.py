#!/usr/local/bin/python

import re
import sys
import os
import getopt
import math
import tempfile
import stat
import subprocess
from optparse import OptionParser
from experiments.run_depparse import DPExpParams
from glob import glob
from experiments.core.util import get_all_following, get_following, get_time, get_following_literal,\
    to_str, to_int, get_group1, head, get_all_matches
from experiments.core.scrape import Scraper, RprojResultsWriter,\
    GoogleResultsWriter, CsvResultsWriter
from experiments.core.util import tail
from random import sample
from experiments.core import scrape

# Commented out to deal with a stray comma.
#_re_stat_elem = re.compile('\S+=(\S+)')
_re_stat_elem = re.compile('\S+=([^,\s]+)')
_re_logging_time = re.compile('^(\d+)\s')


class BnbStatus(DPExpParams):
        
    def __init__(self, status):
        DPExpParams.__init__(self)
        self.update(time = _re_logging_time.search(status).group(1))
        matches = [x for x in _re_stat_elem.finditer(status)]
        self.update(upBound = float(matches[0].group(1)),
                    lowBound = float(matches[1].group(1)),
                    relativeDiff = float(matches[2].group(1)),
                    numLeaves = int(matches[3].group(1)),
                    numFathom = int(matches[4].group(1)),
                    numPrune = int(matches[5].group(1)),
                    numInfeasible = int(matches[6].group(1)),
                    avgFathomDepth = float(matches[7].group(1)),
                    numSeen = int(matches[8].group(1)))
        
        
def get_bnb_status_list(stdout_lines):
    '''Gets a list of BnbStatus objects from summary lines in stdout'''
    status_list = get_all_following(stdout_lines, ".*LazyBranchAndBoundSolver - Summary: ", True)
    if status_list == None:
        return None
    print len(status_list)
    # Choose every $n$th node.
    nth_list = [status_list[i] for i in xrange(0, len(status_list), 1000)]
    print len(nth_list)

    # Downsample the summaries if there are too many
    max_samples = 500
    if len(status_list) > max_samples:
        stride = len(status_list) / max_samples
        status_list = [status_list[i] for i in xrange(0, len(status_list), stride)]
        #status_list = sample(status_list, max_samples)
    print len(status_list)

    status_list = nth_list + status_list
    return map(lambda x: BnbStatus(x), status_list)


class CurNodeStatus(DPExpParams):
        
    def __init__(self, status):
        DPExpParams.__init__(self)
        self.update(time = _re_logging_time.search(status).group(1))
        matches = [x for x in _re_stat_elem.finditer(status)]
        self.update(id = int(matches[0].group(1)),
                    depth = int(matches[1].group(1)),
                    side = int(matches[2].group(1)),
                    upperBound = float(matches[3].group(1)),
                    relaxStatus = matches[4].group(1),
                    incumbentScore = matches[5].group(1),
                    avgNodeTime = float(matches[6].group(1)))
       
       
def get_curnode_status_list(stdout_lines):
    '''Gets a list of current node statuses from lines in stdout''' 
    status_list = get_all_following(stdout_lines, ".*CurrentNode: ", True)
    if status_list == None:
        return None
    return map(lambda x: CurNodeStatus(x), status_list)

    
def get_incumbent_status_list(stdout_lines):
    '''Gets a list of incumbent statuses from lines in stdout''' 
    ll_list = get_all_matches(stdout_lines, "(\d+).*Incumbent logLikelihood: (.*)")
    acc_list = get_all_matches(stdout_lines, "(\d+).*Incumbent accuracy: (.*)")
    if ll_list == None or acc_list == None:
        return None
    assert len(ll_list) == len(acc_list)
    
    # Use the timestamp from the log-likelihood.
    # TODO: depending on the timestamp from the Logger in this way is very brittle.
    status_list = []
    for ll_match, acc_match in zip(ll_list, acc_list):
        ll_time_ms = int(ll_match.group(1))
        ll = float(ll_match.group(2))
        acc_time_ms = int(acc_match.group(1))
        acc = float(acc_match.group(2))
        
        # Double check that the times are at least close (within 3 sec).
        assert abs(ll_time_ms - acc_time_ms) < 3000
        
        status_list.append(DPExpParams(time=ll_time_ms, 
                                       incumbentLogLikelihood=ll, 
                                       incumbentAccuracy=acc))
    return status_list


class DpSingleScraper(Scraper):
    
    def __init__(self, options):
        Scraper.__init__(self, options)
        self.type = options.type

    def get_exp_params_instance(self):
        return DPExpParams()
    
    def get_column_order(self, exp_list):
        hs = "dataset maxNumSentences maxSentenceLength parser model formulation"
        hs += " deltaGenerator factor interval numPerSide"
        hs += " accuracy elapsed error iterations timeRemaining"
        return hs.split()
    
    def scrape_exp_statuses(self, exp, exp_dir, stdout_file):
        
        # Get the status list of the appropriate type.
        status_list = None
        if self.type == "incumbent":
            stdout_lines = self.read_grepped_lines(stdout_file, "Incumbent", ".grep_incumbent")
            status_list = get_incumbent_status_list(stdout_lines)
        elif self.type == "bnb":
            stdout_lines = self.read_grepped_lines(stdout_file, "LazyBranchAndBoundSolver - Summary", ".grep_bnb")
            status_list = get_bnb_status_list(stdout_lines)
        elif self.type == "curnode":
            stdout_lines = self.read_grepped_lines(stdout_file, "CurrentNode", ".grep_curnode")
            status_list = get_curnode_status_list(stdout_lines)
        else:
            raise Exception()

        # Combine the status objects with the experiment definition. 
        return [exp + status for status in status_list]
    
    def read_grepped_lines(self, stdout_file, pattern, suffix=".grep"):
        # TODO: maybe move this into Scraper.
        grepped_file = stdout_file + suffix
        os.system('grep "%s" %s > %s' % (pattern, stdout_file, grepped_file))
        stdout_lines = self.read_stdout_lines(grepped_file)
        return stdout_lines

def parse_options(argv):
    usage = "%prog [top_dir...]"

    parser = OptionParser(usage=usage)
    scrape.add_options(parser)
    parser.add_option('--type', help="The type of status info to scrape [bnb, curnode, incumbent]")
    (options, args) = parser.parse_args(argv)
    
    return parser, options, args

if __name__ == "__main__":
    parser, options, args = parse_options(sys.argv)
    
    if len(args) < 2 or options.type is None:
        parser.print_help()
        sys.exit(1)
    
    exp_dirs = args[1:]
    scraper = DpSingleScraper(options)
    scraper.scrape_exp_dirs(exp_dirs)
