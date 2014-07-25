package edu.jhu.autodiff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import edu.jhu.autodiff.tensor.Exp;
import edu.jhu.autodiff.tensor.ScalarAdd;
import edu.jhu.autodiff.tensor.Sum;
import edu.jhu.prim.vector.IntDoubleDenseVector;
import edu.jhu.util.collections.Lists;
import edu.jhu.util.semiring.Algebra;
import edu.jhu.util.semiring.RealAlgebra;

public class TopoOrderTest {

    private Algebra s = new RealAlgebra();

    @Test
    public void testSimple() {        
        Tensor t1 = TensorUtils.getVectorFromValues(s, 2, 3, 5);

        TensorIdentity id1 = new TensorIdentity(t1);        
        Sum s = new Sum(id1);        
        ScalarAdd add = new ScalarAdd(id1, s, 0);
      
        TopoOrder<Tensor> topo = new TopoOrder<Tensor>(add);
        assertEquals(Lists.getList(), topo.getInputs());

        Tensor t = topo.forward();
        System.out.println(t);
        double sum = 2+3+5;
        assertEquals(2 + sum, t.getValue(0), 1e-13);
        assertEquals(3 + sum, t.getValue(1), 1e-13);
        assertEquals(5 + sum, t.getValue(2), 1e-13);
        
        topo.getOutputAdj().fill(2.2);
        
        topo.backward();
        Tensor xAdj = id1.getOutputAdj();
        System.out.println(xAdj);
        assertEquals(2.2 + 2.2*3, xAdj.getValue(0), 1e-13);
        assertEquals(2.2 + 2.2*3, xAdj.getValue(1), 1e-13);
        assertEquals(2.2 + 2.2*3, xAdj.getValue(2), 1e-13);        
    }
    
    @Test
    public void testGetInputs() {
        // Input.
        TensorIdentity id1 = new TensorIdentity(TensorUtils.getVectorFromValues(s, 2, 3, 5));
        TensorIdentity id2 = new TensorIdentity(TensorUtils.getVectorFromValues(s, 4, 6, 7));
        
        // TopoOrder
        Sum s = new Sum(id1);
        ScalarAdd add = new ScalarAdd(id2, s, 0);
        Exp exp = new Exp(add);
        
        TopoOrder<Tensor> topo = new TopoOrder<Tensor>(Lists.getList(id1, id2), exp);
        
        assertTrue(Lists.getList(id1, id2).equals(topo.getInputs()) ||
                Lists.getList(id2, id1).equals(topo.getInputs()));
    }
    
    @Test
    public void testGradByFiniteDiffs() {
        Tensor t1 = TensorUtils.getVectorFromValues(s, 2, 3, 5);
        TensorIdentity id1 = new TensorIdentity(t1);
        Sum s = new Sum(id1);
        ScalarAdd add = new ScalarAdd(id1, s, 0);
        Exp exp = new Exp(add);
        
        TopoOrder<Tensor> topo = new TopoOrder<Tensor>(Lists.getList(id1), exp);
        
        List<? extends Module<?>> order = topo.getTopoOrder();
        assertTrue(order.contains(s));
        assertTrue(order.contains(add));
        assertTrue(order.contains(exp));
        
        assertTrue(Lists.getList(s, add, exp).equals(topo.getTopoOrder()));
        assertTrue(Lists.getList(id1).equals(topo.getInputs()));
        
        ModuleTestUtils.assertFdAndAdEqual(topo, 1e-5, 1e-8);

        int numParams = 3;                
        IntDoubleDenseVector x = ModuleTestUtils.getAbsZeroOneGaussian(numParams);
        x.set(0, .2);
        x.set(1, .3);
        x.set(2, .5);
        ModuleTestUtils.assertFdAndAdEqual(topo, x, 1e-5, 1e-8);
    }
    
}
