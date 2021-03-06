/*
 *
 *  * Copyright 2016 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.nn.conf.graph;


import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.inputs.InvalidInputTypeException;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.shade.jackson.annotation.JsonProperty;

import java.util.Arrays;

/**
 * StackVertex allows for stacking of inputs so that they may be forwarded through
 * a network. This is useful for cases such as Triplet Embedding, where shared parameters
 * are not supported by the network.
 *
 * @author Justin Long (crockpotveggies)
 */
public class StackVertex extends GraphVertex {

    public StackVertex() {}

    @Override
    public StackVertex clone() {
        return new StackVertex();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StackVertex;
    }

    @Override
    public int numParams(boolean backprop) {
        return 0;
    }

    @Override
    public int hashCode(){
        return 433682566;
    }

    @Override
    public org.deeplearning4j.nn.graph.vertex.GraphVertex instantiate(ComputationGraph graph, String name, int idx,
                                                                      INDArray paramsView, boolean initializeParams) {
        return new org.deeplearning4j.nn.graph.vertex.impl.StackVertex(graph, name, idx);
    }

    @Override
    public InputType getOutputType(InputType... vertexInputs) throws InvalidInputTypeException {
        if(vertexInputs.length == 1) return vertexInputs[0];
        InputType first = vertexInputs[0];
        if(first.getType() == InputType.Type.CNNFlat){
            //TODO
            //Merging flattened CNN format data could be messy?
            throw new InvalidInputTypeException("Invalid input: StackVertex cannot currently merge CNN data in flattened format. Got: " + vertexInputs);
        } else if(first.getType() != InputType.Type.CNN){
            //FF or RNN data inputs
            int size = 0;
            InputType.Type type = null;
            for( int i=0; i<vertexInputs.length; i++ ){
                if(vertexInputs[i].getType() != first.getType()){
                    throw new InvalidInputTypeException("Invalid input: StackVertex cannot merge activations of different types:"
                        + " first type = " + first.getType() + ", input type " + (i+1) + " = " + vertexInputs[i].getType());
                }

                int thisSize;
                switch(vertexInputs[i].getType()){
                    case FF:
                        thisSize = ((InputType.InputTypeFeedForward)vertexInputs[i]).getSize();
                        type = InputType.Type.FF;
                        break;
                    case RNN:
                        thisSize = ((InputType.InputTypeRecurrent)vertexInputs[i]).getSize();
                        type = InputType.Type.RNN;
                        break;
                    default:
                        throw new IllegalStateException("Unknown input type: " + vertexInputs[i]);  //Should never happen
                }
                if(thisSize <= 0){//Size is not defined
                    size = -1;
                } else {
                    size += thisSize;
                }
            }

            if(size > 0){
                //Size is specified
                if(type == InputType.Type.FF) return InputType.feedForward(size);
                else return InputType.recurrent(size);
            } else {
                //size is unknown
                if(type == InputType.Type.FF) return InputType.feedForward(-1);
                else return InputType.recurrent(-1);
            }
        } else {
            //CNN inputs... also check that the depth, width and heights match:
            InputType.InputTypeConvolutional firstConv = (InputType.InputTypeConvolutional)first;
            int fd = firstConv.getDepth();
            int fw = firstConv.getWidth();
            int fh = firstConv.getHeight();

            int depthSum = fd;

            for( int i=1; i<vertexInputs.length; i++ ){
                if(vertexInputs[i].getType() != InputType.Type.CNN){
                    throw new InvalidInputTypeException("Invalid input: StackVertex cannot process activations of different types:"
                        + " first type = " + InputType.Type.CNN + ", input type " + (i+1) + " = " + vertexInputs[i].getType());
                }

                InputType.InputTypeConvolutional otherConv = (InputType.InputTypeConvolutional) vertexInputs[i];

                int od = otherConv.getDepth();
                int ow = otherConv.getWidth();
                int oh = otherConv.getHeight();

                if(fw != ow || fh != oh){
                    throw new InvalidInputTypeException("Invalid input: StackVertex cannot merge CNN activations of different width/heights:"
                        + "first [depth,width,height] = [" + fd + "," + fw + "," + fh + "], input " + i + " = [" + od + "," + ow + "," + oh + "]");
                }

                depthSum += od;
            }

            return InputType.convolutional(fh,fw,depthSum);
        }
    }
}
