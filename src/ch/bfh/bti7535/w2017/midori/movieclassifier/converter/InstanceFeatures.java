package ch.bfh.bti7535.w2017.midori.movieclassifier.converter;

import ch.bfh.bti7535.w2017.midori.movieclassifier.converter.ArffGenerator.Label;

public class InstanceFeatures {

  private Label label = null;
  private int positive, negative, strong, weak = 0;
  
  public InstanceFeatures(Label label) {
    this.label = label;
  }
  
  public int getPositive() {
    return positive;
  }

  public int getNegative() {
    return negative;
  }

  public int getStrong() {
    return strong;
  }

  public int getWeak() {
    return weak;
  }
  
  public void addWord(WordConnotation wc) {
    if(wc.isPositive())
      addPositive();
    if(wc.isNegative())
      addNegative();
    if(wc.isStrong())
      addStrong();
    if(wc.isWeak())
      addWeak();
  }
  
  private void addPositive() {
    positive++;
  }
  
  private void addNegative() {
    negative++;
  }
  
  private void addStrong() {
    strong++;
  }
  
  private void addWeak() {
    weak++;
  }
  
}
