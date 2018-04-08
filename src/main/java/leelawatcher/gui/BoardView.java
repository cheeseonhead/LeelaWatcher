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

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * @author Gus
 */
public class BoardView extends javax.swing.JPanel {

  public interface BoardViewDelegate {
    void message(String str);
  }

  public interface SetGameInfo {
    void lam(String str);
  }

  private static final Dimension PREFERRED_SIZE = new Dimension(500, 500);
  public static int POST_ENDGAME_THRESHOLD = 300;
  public static int SIMUL_GAME_THRESHOLD = 10;

  public BoardViewDelegate delegate;
  public SetGameInfo setGameInfo;

  private HashMap<String, BoardViewModel> boards;
  private HashMap<String, BoardViewModel> finishedBoards;
  private ArrayList<BoardViewModel> boardList;
  private BoardViewModel curBoard;
  private ImageMaker goImages = new ImageMaker();

  /**
   * Creates new form boardView
   */
  BoardView() {
    boardList = new ArrayList<>();
    boards = new HashMap<>();
    finishedBoards = new HashMap<>();
  }

  public void paint(java.awt.Graphics g) {
    // Find out how much space is available.
    super.paint(g);

    BoardViewModel curBoardVM = getBoardToDisplay();

    if(curBoardVM == null) {
      return;
    }

    Board board = curBoardVM.getBoard();

    setGameInfo.lam(gameNumStr(curBoardVM) + " " + curBoardVM.gameInfo());

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

    java.awt.image.BufferedImage boardImg = goImages.paintBoard(makeSize, lines, board.getCurrPos());

    g.drawImage(boardImg, ((availW - makeSize) / 2), ((availH - makeSize) / 2), this);
  }

  public void update(java.awt.Graphics g) {
    paint(g);
  }

  public void move(PointOfPlay pop, String seed, int moveNum) throws IllegalMoveException {

    if (!boards.containsKey(seed)) {
      return;
    }

    Board board = boards.get(seed).getBoard();

    if (pop != null) {
      board.doMove(pop.getX(), pop.getY());
    } else {
      // pass
      board.doMove(Move.PASS, Move.PASS);
    }

    board.setMoveNum(moveNum);

    repaint();
  }

  public void previousBoard() {

    int index = boardList.indexOf(curBoard);

    if(index > 0) {
      curBoard = boardList.get(index-1);
    }

    repaint();
  }

  public void nextBoard() {
    int index = boardList.indexOf(curBoard);

    if(index < boardList.size() - 1) {
      curBoard = boardList.get(index+1);
    }

    repaint();
  }

  public void addNewBoard(String seed, BoardViewModel.Type type) {
    BoardViewModel newBoard = new BoardViewModel(seed, type);

//    System.out.println("Adding board: " + seed + "...");

    boards.put(seed, newBoard);
    addBoardToList(newBoard);

//    System.out.println("Boards: " + boards.toString());
  }

  public void resultBoard(String seed, String score) {

    if(boards.containsKey(seed)) {
      BoardViewModel board = boards.get(seed);
      board.setScore(score);
      finishedBoards.put(seed, board);
      boards.remove(seed);
    }
  }

  public void reset() {
    boards.clear();
    finishedBoards.clear();
    curBoard = null;

//    System.out.println("Resetting...\nBoards: " + boards.toString() + " finishedBoards: " + finishedBoards.toString() + " current seed: " + currentDisplaySeed);
  }

  private void addBoardToList(BoardViewModel board) {
    boardList.add(board);

    while(boardList.size() > SIMUL_GAME_THRESHOLD) {
      boardList.remove(0);
    }

    int index = boardList.indexOf(curBoard);

    if(index == boardList.size() - 1) {
      this.nextBoard();
    }
  }

  private void printBoardList() {
    System.out.println("[");
    for(BoardViewModel vm: boardList) {
      System.out.println(vm.getSeed());
    }
    System.out.println("]");
  }

  private BoardViewModel getBoardToDisplay() {

    if (curBoard == null || !boardList.contains(curBoard)) {
      curBoard = boardList.size() > 0 ? boardList.get(0) : null;
    }

    return curBoard;
  }

  private String gameNumStr(BoardViewModel model) {
    if(boardList.contains(model)) {
      int index = boardList.indexOf(model);

      return (index+1) + "/" + boardList.size();
    }
    else {
      return "";
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return PREFERRED_SIZE;
  }

  void saveGames() {

//    System.out.println("Finished boards: " + finishedBoards.toString());

    for(Map.Entry<String, BoardViewModel> entry: finishedBoards.entrySet()) {
      BoardViewModel boardVM = entry.getValue();

//      System.out.println("Got game: " + seed);

      String format = DateTimeFormatter.ISO_INSTANT
              .format(new Date().toInstant()).replaceAll(":", "_");
      format += "_" + boardVM.getSeed();
      File file = new File(format + ".sgf");
//      System.out.println("Saving as:" + file);
      boardVM.getBoard().saveGame(file.getPath());
    }

//    System.out.println("Clearing finished boards...");

    finishedBoards.clear();

//    System.out.println("Finished boards: " + finishedBoards.toString());
  }

}
