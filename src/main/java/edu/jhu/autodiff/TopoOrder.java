package edu.jhu.autodiff;

import java.util.HashSet;
import java.util.List;

import edu.jhu.autodiff.Toposort.Deps;
import edu.jhu.util.collections.Lists;
import edu.jhu.util.semiring.Algebra;


/**
 * Topographically ordered list of modules, combined into a single module. This module will call
 * foward() in topo-order and backward() in reverse topo-order on all the modules.
 * 
 * @author mgormley
 */
public class TopoOrder<Y extends ModuleTensor<Y>> implements Module<Y> {

    private List<? extends Module<?>> inputs;
    private Module<Y> outMod;
    private List<? extends Module<?>> topoOrder;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public TopoOrder(Module<Y> root) {
        this.inputs = Lists.getList();
        this.outMod = root;
        Deps deps = getModuleDeps();
        this.topoOrder = Toposort.toposort(root, deps);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public TopoOrder(List<? extends Module<?>> inputsList, Module<Y> root) {
        HashSet inputs = new HashSet(inputsList);
        if (inputs.size() != inputsList.size()) {
            throw new IllegalStateException("Multiple copies of module in inputs list: " + inputsList);
        }
        this.inputs = inputsList;
        this.outMod = root;

        Deps deps = getModuleDeps();
        this.topoOrder = Toposort.toposort(inputs, root, deps);
    }
    
    @SuppressWarnings({ "rawtypes" })
    private static Deps getModuleDeps() {
        Deps deps = new Deps() {
            @Override
            public List getDeps(Object x) {
                return ((Module) x).getInputs();
            }
        };
        return deps;
    }

    @Override
    public Y forward() {
        for (Module<? extends Object> m : topoOrder) {
            m.forward();
        }
        return outMod.getOutput();
    }

    @Override
    public void backward() {
        List<? extends Module<?>> revTopo = Lists.reverse(topoOrder);
        for (Module<?> m : revTopo) {
            m.backward();
        }
    }

    @Override
    public List<? extends Module<?>> getInputs() {
        return inputs;
    }

    @Override
    public Y getOutput() {
        return outMod.getOutput();
    }

    @Override
    public Y getOutputAdj() {
        return outMod.getOutputAdj();
    }
    
    @Override
    public void zeroOutputAdj() {
        for (Module<? extends Object> m : topoOrder) {
            m.zeroOutputAdj();
        }
    }
    
    public List<? extends Module<?>> getTopoOrder() {
        return topoOrder;
    }

    @Override
    public Algebra getAlgebra() {
        return outMod.getAlgebra();
    }
    
}