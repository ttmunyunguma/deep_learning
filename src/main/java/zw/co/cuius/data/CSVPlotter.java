package zw.co.cuius.data;

import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import zw.co.cuius.utils.DownloaderUtility;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Read a csv file. Fit and plot the data using DL4J.
 *
 * @author Terrence
 */

public class CSVPlotter {

    public static String dataLocalPath;

    public static void main(String[] args) throws Exception {
        dataLocalPath = DownloaderUtility.DATAEXAMPLES.Download();
        String filename = new File(dataLocalPath, "CSVPlotData.csv").getAbsolutePath();
        DataSet ds = readCSVDataset(filename);

        ArrayList<DataSet> DataSetList = new ArrayList<>();
        DataSetList.add(ds);

        plotDataset(DataSetList); //Plot the data, make sure we have the right data.

        MultiLayerNetwork net = fitStraightLine(ds);

        // Get the min and max x values, using Nd4j
        NormalizerMinMaxScaler preProcessor = new NormalizerMinMaxScaler();
        preProcessor.fit(ds);
        int nSamples = 50;
        INDArray x = Nd4j.linspace(preProcessor.getMin().getInt(0), preProcessor.getMax().getInt(0), nSamples).reshape(nSamples, 1);
        INDArray y = net.output(x);
        DataSet modelOutput = new DataSet(x, y);
        DataSetList.add(modelOutput);

        plotDataset(DataSetList);    //Plot data and model fit.
    }

    /**
     * Fit a straight line using a neural network.
     *
     * @param ds The dataset to fit.
     * @return The network fitted to the data
     */
    private static MultiLayerNetwork fitStraightLine(DataSet ds) {
        int seed = 12345;
        int nEpochs = 200;
        double learningRate = 0.00001;
        int numInputs = 1;
        int numOutputs = 1;

        //
        // Hook up one input to the one output.
        // The resulting model is a straight line.
        //
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .weightInit(WeightInit.XAVIER)
                .updater(new Nesterovs(learningRate, 0.9))
                .list()
                .layer(new DenseLayer.Builder().nIn(numInputs).nOut(numOutputs)
                        .activation(Activation.IDENTITY)
                        .build())
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .activation(Activation.IDENTITY)
                        .nIn(numOutputs).nOut(numOutputs).build())
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.setListeners(new ScoreIterationListener(1));

        for (int i = 0; i < nEpochs; i++) {
            net.fit(ds);
        }

        return net;
    }

    /**
     * Read a CSV file into a dataset.
     * <p>
     * Use the correct constructor:
     * DataSet ds = new RecordReaderDataSetIterator(rr,batchSize);
     * returns the data as follows:
     * ===========INPUT===================
     * [[12.89, 22.70],
     * [19.34, 20.47],
     * [16.94,  6.08],
     * [15.87,  8.42],
     * [10.71, 26.18]]
     * <p>
     * Which is not the way the framework likes its data.
     * <p>
     * This one:
     * RecordReaderDataSetIterator(rr,batchSize, 1, 1, true);
     * returns
     * ===========INPUT===================
     * [12.89, 19.34, 16.94, 15.87, 10.71]
     * =================OUTPUT==================
     * [22.70, 20.47,  6.08,  8.42, 26.18]
     * <p>
     * This can be used as is for regression.
     */
    private static DataSet readCSVDataset(String filename) throws IOException, InterruptedException {
        int batchSize = 1000;
        RecordReader rr = new CSVRecordReader();
        rr.initialize(new FileSplit(new File(filename)));

        DataSetIterator iter = new RecordReaderDataSetIterator(rr, batchSize, 1, 1, true);
        return iter.next();
    }

    /**
     * Generate an xy plot of the datasets provided.
     */
    private static void plotDataset(ArrayList<DataSet> DataSetList) {

        XYSeriesCollection c = new XYSeriesCollection();

        int dsCounter = 1; //use to name the data-series
        for (DataSet ds : DataSetList) {
            INDArray features = ds.getFeatures();
            INDArray outputs = ds.getLabels();

            int nRows = features.rows();
            XYSeries series = new XYSeries("S" + dsCounter);
            for (int i = 0; i < nRows; i++) {
                series.add(features.getDouble(i), outputs.getDouble(i));
            }

            c.addSeries(series);
        }

        String title = "title";
        String xAxisLabel = "xAxisLabel";
        String yAxisLabel = "yAxisLabel";
        PlotOrientation orientation = PlotOrientation.VERTICAL;
        boolean legend = false;
        boolean tooltips = false;
        boolean urls = false;
        JFreeChart chart = ChartFactory.createScatterPlot(title, xAxisLabel, yAxisLabel, c, orientation, legend, tooltips, urls);
        JPanel panel = new ChartPanel(chart);

        JFrame f = new JFrame();
        f.add(panel);
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.pack();
        f.setTitle("Training Data");

        f.setVisible(true);
    }

}
