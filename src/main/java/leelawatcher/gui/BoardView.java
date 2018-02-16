/*
    Copyright 2017 Patrick G. Heck

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */

/*
 * boardView.java
 *
 * Created on December 16, 2002, 12:59 AM
 */

package leelawatcher.gui;

import leelawatcher.goboard.Board;
import leelawatcher.goboard.IllegalMoveException;
import leelawatcher.goboard.Move;
import leelawatcher.goboard.PointOfPlay;

import java.awt.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;


/**
 * @author Gus
 */
public class BoardView extends javax.swing.JPanel {

  public interface BoardViewDelegate {
    void message(String str);
  }

  private static final Dimension PREFERRED_SIZE = new Dimension(500, 500);
  private static final int POST_ENDGAME_THRESHOLD = 300;

  public BoardViewDelegate delegate;

  private HashMap<String, Board> boards;
  private HashMap<String, Board> finishedBoards;
  private String currentDisplaySeed = "";
  private ImageMaker goImages = new ImageMaker();

  /**
   * Creates new form boardView
   */
  BoardView() {
    boards = new HashMap<>();
    finishedBoards = new HashMap<>();
  }

  public void paint(java.awt.Graphics g) {
    // Find out how much space is available.
    super.paint(g);

    Board curBoard = getBoardToDisplay();

    if(curBoard == null) {
      return;
    }

    Container p = getParent();
    g.setColor(p.getBackground());
    int availH = getHeight();
    int availW = getWidth();
    g.fillRect(0, 0, availW, availH);

    int lines = 19;

    // We have to keep things square so choose the lesser one...

    int makeSize = Math.min(availH, availW);
    makeSize = Math.max(21, makeSize);        // but not too small...

    // Now, we have to draw a whole board in case the GUI also needs some part of
    // the board painted. (Something may be covered/revealed between the
    // call to repaint() from placing a stone, and when the GUI got around to
    // calling paint() for example)

    java.awt.image.BufferedImage boardImg = goImages.paintBoard(makeSize, lines, curBoard.getCurrPos());

    g.drawImage(boardImg, ((availW - makeSize) / 2), ((availH - makeSize) / 2), this);
  }

  public void update(java.awt.Graphics g) {
    paint(g);
  }

  public void move(PointOfPlay pop, String seed, int moveNum) throws IllegalMoveException {

    if (!boards.containsKey(seed)) {
      return;
    }

    Board board = boards.get(seed);

    if (pop != null) {
      board.doMove(pop.getX(), pop.getY());
    } else {
      // pass
      board.doMove(Move.PASS, Move.PASS);
    }

    board.setMoveNum(moveNum);

    repaint();
  }

  public void addNewBoard(String seed, Board.Type type) {
    Board newBoard = new Board(type);

    boards.put(seed, newBoard);
  }

  public void finishBoard(String seed) {
    if(boards.containsKey(seed)) {
      Board board = boards.get(seed);

      finishedBoards.put(seed, board);
      boards.remove(seed);
    }
  }

  private Board getBoardToDisplay() {

    String bestSeed = currentDisplaySeed;

    for(Map.Entry<String, Board> entry: boards.entrySet()) {
      String seed = entry.getKey();

      int seedPriority = priorityOfSeed(seed);
      int bestPriority = priorityOfSeed(bestSeed);

      if(seedPriority > bestPriority) {
        bestSeed = seed;
      }
    }

    Board board = boards.getOrDefault(bestSeed, null);

    if(!bestSeed.equals(currentDisplaySeed)) {
      currentDisplaySeed = bestSeed;

      if(delegate != null && board != null) {
        delegate.message("Playing " + board.getType().getStr() + " starting at " + board.getMoveNum() + " game: " + currentDisplaySeed);
      }
    }

    return board;
  }

  private int priorityOfSeed(String seed) {

    if(!boards.containsKey(seed)) {
      return 0;
    }

    Board board = boards.get(seed);

    if(board.isGameOver()) {
      return 0;
    }

    int priority = 0;

    switch(board.getType()) {
      case match:
        priority = 60;
        break;
      case selfplay:
        priority = 50;
        break;
    }

    if (board.getMoveNum() >= POST_ENDGAME_THRESHOLD) {
      priority -= 20;
    }

    if (seed.equals(currentDisplaySeed)) {
      priority += 5;
    }

    return priority;
  }

  @Override
  public Dimension getPreferredSize() {
    return PREFERRED_SIZE;
  }

  void saveGames() {
    for(Map.Entry<String, Board> entry: finishedBoards.entrySet()) {
      String seed = entry.getKey();
      Board board = entry.getValue();

      if(board.isGameOver()) {
        String format = DateTimeFormatter.ISO_INSTANT
                .format(new Date().toInstant()).replaceAll(":", "_");
        format += "_" + seed;
        File file = new File(format + ".sgf");
        System.out.println("Saving as:" + file);
        board.saveGame(file.getPath());

        finishedBoards.clear();
      }
    }
  }
}
