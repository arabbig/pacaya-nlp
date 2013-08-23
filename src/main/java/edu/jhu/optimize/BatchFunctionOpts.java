package edu.jhu.optimize;

import edu.jhu.util.math.Vectors;

public class BatchFunctionOpts {

    /** Wrapper which negates the input function. */
    public static class NegateFunction implements BatchFunction {
    
        private BatchFunction function;
        
        public NegateFunction(BatchFunction function) {
            this.function = function;
        }
        
        @Override
        public void setPoint(double[] point) {
            function.setPoint(point);
        }
        
        @Override
        public double getValue(int[] batch) {
            return - function.getValue(batch);
        }
    
        @Override
        public void getGradient(int[] batch, double[] gradient) {
            function.getGradient(batch, gradient);
            Vectors.scale(gradient, -1.0);
        }
    
        @Override
        public int getNumDimensions() {
            return function.getNumDimensions();
        }

        @Override
        public int getNumExamples() {
            return function.getNumExamples();
        }
    
    }
    
    /** Wrapper which adds the input functions. */
    public static class AddFunctions implements BatchFunction {
    
        private BatchFunction[] functions;
        
        public AddFunctions(BatchFunction... functions) {
            int numDims = functions[0].getNumDimensions();
            int numExs = functions[0].getNumExamples();
            for (BatchFunction f : functions) {
                if (numDims != f.getNumDimensions()) {
                    throw new IllegalArgumentException("Functions have different dimension.");
                }
                if (numExs != f.getNumExamples()) {
                    throw new IllegalArgumentException("Functions have different numbers of examples.");
                }
            }
            this.functions = functions;
        }
        
        @Override
        public void setPoint(double[] point) {
            for (BatchFunction function : functions) {
                function.setPoint(point);
            }
        }
        
        @Override
        public double getValue(int[] batch) {
            double sum = 0.0;
            for (BatchFunction f : functions) {
                sum += f.getValue(batch);                
            }
            return sum;
        }
    
        @Override
        public void getGradient(int[] batch, double[] gradient) {
            double[] g = new double[getNumDimensions()];
            for (BatchFunction f : functions) {
                f.getGradient(batch, g);
                Vectors.add(gradient, g);
            }
        }
    
        @Override
        public int getNumDimensions() {
            return functions[0].getNumDimensions();
        }

        @Override
        public int getNumExamples() {
            return functions[0].getNumExamples();
        }
    
    }

}