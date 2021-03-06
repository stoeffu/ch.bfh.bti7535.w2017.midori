package ch.bfh.bti7535.w2017.midori.movieclassifier.converter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;

import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.stemmers.SnowballStemmer;
import weka.core.stemmers.Stemmer;

/**
 * This class takes review txt files as inputs, extracts their features and converts them to an ARFF file
 */
public class ArffGenerator {

  /**
   * The labels / classes an instance can have
   */
  public enum Label {
    POSITIVE, NEGATIVE
  }

  private final static List<String> negativeMarkers = Arrays.asList("n't", "not");
  private final static String negativePrefix = "NOT_";

  private final static File lexiconInputFile = new File("data" + File.separator + "general_inquirer_lexicon" + File.separator + "inquirerbasic.csv");
  private static final Map<String, WordConnotation> connotationLexicon = new HashMap<>();

  private final static String trainingDataFolderPrefix = "data" + File.separator + "txt_sentoken" + File.separator;
  private final static File negativeInstancesFolder = new File(trainingDataFolderPrefix + "neg");
  private final static File positiveInstancesFolder = new File(trainingDataFolderPrefix + "pos");

  private final static File csvOutputFile = new File("data" + File.separator + "movie-reviews.csv");
  private final static File arffOutputFile = new File("data" + File.separator + "movie-reviews.arff");

  private final static List<String> relevantCols = Arrays.asList("Entry", "Positiv", "Negativ", "Hostile", "Strong", "Weak", "Pleasur", "Arousal",
      "Pain", "Virtue", "Hostile");
  private final static Pattern wordRecognitionPattern = Pattern.compile(".*[A-Za-z]+.*");
  private final static Pattern punctuationRecognitionPattern = Pattern.compile("[.,!?\\-]");
  private final static Pattern wordDelimiterPattern = Pattern.compile("[^A-Za-z]");


  public static void main(String args[]) throws IOException {

    SnowballStemmer stemmer = new SnowballStemmer();
    loadLexicon(stemmer);

    CSVWriter csvWriter = new CSVWriter(new FileWriter(csvOutputFile));
    // write attribute titles to CSV file
    csvWriter.writeNext(InstanceFeatures.titles());

    // iterate through all positively labeled files and add its features to the CSV file
    File[] files = positiveInstancesFolder.listFiles();
    for (File file : Objects.requireNonNull(files)) {
      String comment = loadCommentFile(file);
      comment = stem(stemmer, comment);
      comment = addNegativeLabels(comment);
      InstanceFeatures features = extractFeatures(comment, Label.POSITIVE);
      csvWriter.writeNext(features.toStringArray());
    }

    // iterate through all negatively labeled files and add its features to the CSV file
    files = negativeInstancesFolder.listFiles();
    for (File file : Objects.requireNonNull(files)) {
      String comment = loadCommentFile(file);
      comment = stem(stemmer, comment);
      comment = addNegativeLabels(comment);
      InstanceFeatures features = extractFeatures(comment, Label.NEGATIVE);
      csvWriter.writeNext(features.toStringArray());
    }

    csvWriter.close();

    // convert CSV file to ARFF file
    CSVLoader csvLoader = new CSVLoader();
    csvLoader.setSource(csvOutputFile);
    Instances data = csvLoader.getDataSet();
    data.setClassIndex(data.numAttributes() - 1);

    BufferedWriter writer = new BufferedWriter(new FileWriter(arffOutputFile));
    writer.write(data.toString());
    writer.flush();
    writer.close();
  }

  private static String loadCommentFile(File file) throws FileNotFoundException {

    Scanner scanner = new Scanner(file);
    String fileContent = scanner.useDelimiter("\\Z").next();
    scanner.close();
    return fileContent;
  }


  private static String stem(Stemmer stemmer, String comment) {
    return Arrays.asList(
        comment.split(" ")
    ).stream()
        .map(word -> stemmer.stem(word))
        .collect(Collectors.joining(" "));
  }


  private static String addNegativeLabels(String comment) {

    String delimiter = StringUtils.join(negativeMarkers, "|");
    String[] parts = comment.split(delimiter);

    if (parts.length < 2)
      return comment;
    for (int i = 1; i < parts.length; i++) {
      String part = parts[i].toLowerCase();
      String[] words = part.split(" ");
      for (int j = 0; j < words.length; j++) {
        if (words[j].matches(punctuationRecognitionPattern.pattern()))
          break;
        if (!words[j].matches(wordRecognitionPattern.pattern()))
          continue;
        words[j] = negativePrefix + words[j];
      }
      parts[i] = String.join(" ", words);

    }

    // return the converted text
    return String.join(" ", parts);
  }


  private static InstanceFeatures extractFeatures(String text, Label label) {

    InstanceFeatures features = new InstanceFeatures(label);

    String[] words = text.split(" ");
    for (String word : words) {
      boolean negated = false;
      if (word.contains(negativePrefix)) {
        negated = true;
        word = word.substring(negativePrefix.length());
      }
      word = word.toLowerCase();

      if (word.length() < 3 || !connotationLexicon.containsKey(word))
        continue;
      WordConnotation wc = connotationLexicon.get(word);
      features.addWord(wc, negated);
    }

    return features;

  }

  private static void loadLexicon(Stemmer stemmer) throws FileNotFoundException {

    CSVParser parser = new CSVParserBuilder()
        .withSeparator(';')
        .build();
    CSVReader reader = new CSVReaderBuilder(new FileReader(lexiconInputFile))
        .withCSVParser(parser)
        .build();

    Iterator<String[]> it = reader.iterator();
    String[] csvHeader = it.next();

    // build a Map that contains column indices
    Map<String, Integer> csvColMap = getColIndices(csvHeader);

    // iterate through every word in the lexicon and add its connotation info to the connotation lexicon
    while (it.hasNext()) {
      String[] wordInfo = it.next();
      String word = wordInfo[csvColMap.get("Entry")];

      word = word.replaceAll(wordDelimiterPattern.pattern(), "").toLowerCase();

      if (connotationLexicon.containsKey(word))
        continue;

      boolean positive = wordInfo[csvColMap.get("Positiv")].equals("Positiv");
      boolean negative = wordInfo[csvColMap.get("Negativ")].equals("Negativ");
      boolean strong = wordInfo[csvColMap.get("Strong")].equals("Strong");
      boolean weak = wordInfo[csvColMap.get("Weak")].equals("Weak");
      boolean pleasur = wordInfo[csvColMap.get("Pleasur")].equals("Pleasur");
      boolean arousal = wordInfo[csvColMap.get("Arousal")].equals("Arousal");
      boolean pain = wordInfo[csvColMap.get("Pain")].equals("Pain");
      boolean virtue = wordInfo[csvColMap.get("Virtue")].equals("Virtue");
      boolean hostile = wordInfo[csvColMap.get("Hostile")].equals("Hostile");

      word = stemmer.stem(word);
      WordConnotation wc = new WordConnotation(word, positive, negative, strong, weak, pleasur, arousal, pain, virtue, hostile);
      connotationLexicon.put(word, wc);
    }
  }

  private static Map<String, Integer> getColIndices(String[] csvHeader) {
    // Map to map column indices in the csv and the relevant attributes
    Map<String, Integer> csvColMap = new HashMap<>();

    for (int i = 0; i < csvHeader.length; i++) {
      if (relevantCols.contains(csvHeader[i])) {
        csvColMap.put(csvHeader[i], i);
      }
    }
    return csvColMap;
  }
}
