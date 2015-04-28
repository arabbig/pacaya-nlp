package edu.jhu.nlp.data.conll;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhu.pacaya.util.files.FileListIterator;
import edu.jhu.pacaya.util.files.Files;

/**
 * Reads a file or directory of CoNLL-X files.
 * 
 * @author mgormley
 *
 */
public class CoNLLXDirReader implements Iterable<CoNLLXSentence> {

    private static final Logger log = LoggerFactory.getLogger(CoNLLXDirReader.class);

    private List<File> files;
    
    public CoNLLXDirReader(File path) {
        files = Files.getMatchingFiles(path, ".*\\.conll");
    }
    
    public CoNLLXDirReader(String path) {
        this(new File(path));
    }

    @Override
    public Iterator<CoNLLXSentence> iterator() {
        if (files == null) {
            throw new IllegalStateException("loadPath must be called first");
        }
        
        return new FileListIterator<CoNLLXSentence>(files) {
            @Override
            public Iterator<CoNLLXSentence> getIteratorInstance(File file) {
                try {
                    return new CoNLLXFileReader(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
    
}
