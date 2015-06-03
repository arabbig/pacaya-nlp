package edu.jhu.nlp.features;

import java.util.ArrayList;
import java.util.List;

import edu.jhu.nlp.data.conll.CoNLL09Sentence;
import edu.jhu.nlp.data.conll.CoNLL09Token;

public class CoNLL09SentencesForTests {
            
    public static CoNLL09Sentence getSpanishConll09Sentence1() {
        List<CoNLL09Token> tokens = new ArrayList<CoNLL09Token>();  
        //tokens.add(new CoNLL09Token(id, form, lemma, plemma, pos, ppos, feat, pfeat, head, phead, deprel, pdeprel, fillpred, pred, apreds));
        tokens.add(new CoNLL09Token("1       _       _       _       p       p       _       _       2       2       suj     suj     _       _       arg1-tem        _"));
        tokens.add(new CoNLL09Token("2       Resultaban      resultar        resultar        v       v       postype=main|gen=c|num=p|person=3|mood=indicative|tense=imperfect       postype=main|gen=c|num=p|person=3|mood=indicative|tense=imperfect       0       0       sentence        sentence        Y       resultar.c2     _       _"));
        tokens.add(new CoNLL09Token("3       demasiado       demasiado       demasiado       r       r       _       _       4       4       spec    spec    _       _       _       _"));
        tokens.add(new CoNLL09Token("4       baratos barato  barato  a       a       postype=qualificative|gen=m|num=p       postype=qualificative|gen=m|num=p       2       2       cpred   cpred   _       _       arg2-atr        _"));
        tokens.add(new CoNLL09Token("5       para    para    para    s       s       postype=preposition|gen=c|num=c postype=preposition|gen=c|num=c 2       2       cc      cc      _       _       argM-fin        _"));
        tokens.add(new CoNLL09Token("6       ser     ser     ser     v       v       postype=semiauxiliary|gen=c|num=c|mood=infinitive       postype=semiauxiliary|gen=c|num=c|mood=infinitive       5       5       S       S       Y       ser.c2  _       _"));
        tokens.add(new CoNLL09Token("7       buenos  buen    bueno   a       a       postype=qualificative|gen=m|num=p       postype=qualificative|gen=m|num=p       6       6       atr     atr     _       _       _       arg2-atr"));
        tokens.add(new CoNLL09Token("8       .       .       .       f       f       punct=period    punct=period    2       2       f       f       _       _       _       _"));
        CoNLL09Sentence sent = new CoNLL09Sentence(tokens);
        
        return sent;
    }
    
    public static CoNLL09Sentence getSpanishConll09Sentence2() {
        List<CoNLL09Token> tokens = new ArrayList<CoNLL09Token>();      
        tokens.add(new CoNLL09Token("1       Eso     Eso     ESO     n       n       postype=proper|gen=c|num=c      postype=proper|gen=c|num=c      2       0       suj     suj     _       _       arg1-tem        _"));
        tokens.add(new CoNLL09Token("2       es      ser     ser     v       v       postype=semiauxiliary|gen=c|num=s|person=3|mood=indicative|tense=present        postype=semiauxiliary|gen=c|num=s|person=3|mood=indicative|tense=present        0       3       sentence        sentence        Y       ser.c2  _       _"));
        tokens.add(new CoNLL09Token("3       lo      el      el      d       d       postype=article|gen=c|num=s     postype=article|gen=c|num=s     6       4       spec    spec    _       _       _       _"));
        tokens.add(new CoNLL09Token("4       que     que     que     p       p       postype=relative|gen=c|num=c    postype=relative|gen=c|num=c    6       5       cd      cd      _       _       _       arg1-pat"));
        tokens.add(new CoNLL09Token("5       _       _       _       p       WRONG       _       _       6       6       suj     suj     _       _       _       arg0-agt"));
        tokens.add(new CoNLL09Token("6       hicieron        hacer   hacer   v       v       postype=main|gen=c|num=p|person=3|mood=indicative|tense=past    postype=main|gen=c|num=p|person=3|mood=indicative|tense=past    2       7       atr     atr     Y       hacer.a2        arg2-atr        _"));
        tokens.add(new CoNLL09Token("7       .       .       .       f       f       punct=period    punct=period    2       1       f       f       _       _       _       _"));
        CoNLL09Sentence sent = new CoNLL09Sentence(tokens);
            
        return sent;
    }
    
}
