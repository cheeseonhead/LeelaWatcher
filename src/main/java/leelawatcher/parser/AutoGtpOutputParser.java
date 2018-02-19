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
package leelawatcher.parser;

import leelawatcher.goboard.Board;
import leelawatcher.goboard.IllegalMoveException;
import leelawatcher.goboard.PointOfPlay;
import leelawatcher.gui.BoardView;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoGtpOutputParser implements BoardView.BoardViewDelegate {

  /*
   * This pattern is meant to report a match for one of 3 groups:
   * 1. anything ending in 'set.'
   * (?:[BW]\\s)? is added so that both AutoGTPv11 outputs (B A1) (W F18) and AutoGTPv9 outputs (A1) (F18) will work.
   */
  private static final Pattern MOVE_EVENT =
          Pattern.compile("\\s*(\\w+)\\s(\\d+)\\s\\((?:[BW]\\s)?(\\w+)\\)\\s*");
  private static final Pattern GAMEOVER_EVENT =
          Pattern.compile("\\s*(\\w+)\\sGame\\shas\\sended.\\s*");
  private static final Pattern GAMESTART_EVENT =
          Pattern.compile("\\s*Got\\snew\\sjob:\\s(\\w+)\\s*");
  private static final Pattern ERROR_EVENT =
          Pattern.compile("\\s*\\*ERROR\\*:\\s(.+)\\s*");
  private static final Pattern SCORE_EVENT =
          Pattern.compile("\\s*Score:\\s(.*)\\s*");

  private static final Pattern MOVE = Pattern.compile("(?:(.)(\\d+))|(pass)|(resign)");

  private BoardView boardView;
  private boolean inProgress = false;

  private Board.Type upcomingGameType = null;

  @SuppressWarnings("unused")
  public String getMessage() {
    return message;
  }

  private void setMessage(String message) {
    String old = this.message;
    this.message = message;
    support.firePropertyChange("message", old, message);
  }

  private String message;

  private PropertyChangeSupport support = new PropertyChangeSupport(this);


  /**
   * Dead simple parser for the standard output from leela autogtp
   *
   * @param boardView the view on which to reflect the output.
   */
  public AutoGtpOutputParser(BoardView boardView) {
    this.boardView = boardView;
    if(this.boardView != null)
      this.boardView.delegate = this;
  }

  public void start(InputStream is) {
    Executors.newSingleThreadExecutor().submit(() -> {
      StringBuffer buffer = new StringBuffer();
      int next;
      try {
        //noinspection InfiniteLoopStatement
        while ((next = is.read()) != -1) {
          buffer.append((char) next);
          // System.out.print("" + (char) next);
          String event = nextLine(buffer);
          if (event == null) {
            continue;
          }

          Matcher moveMatcher = MOVE_EVENT.matcher(event);
          Matcher gameOverMatcher = GAMEOVER_EVENT.matcher(event);
          Matcher gameStartMatcher = GAMESTART_EVENT.matcher(event);
          Matcher errorMatcher = ERROR_EVENT.matcher(event);
          Matcher scoreMatcher = SCORE_EVENT.matcher(event);

          if (gameStartMatcher.matches()) {

            System.out.println("EVENT: Game start");

            String gameType = gameStartMatcher.group(1);
            int gamePriority = priority(gameType);

            System.out.println("Type: " + gameType + " Priority: " + gamePriority);

            upcomingGameType = parseType(gameType);
          }
          else if (moveMatcher.matches()) {

            System.out.println("EVENT: Move");

            String seed = moveMatcher.group(1);
            int moveNum = Integer.parseInt(moveMatcher.group(2));
            String mv = moveMatcher.group(3);

            System.out.println("Move Number: " + moveNum + " Location: " + mv + " Seed: " + seed);

            if(moveNum == 1) {
              boardView.addNewBoard(seed, upcomingGameType);
            }

            setInProgress(true);
//            message("Playing move " + moveNum + " " + mv + " seed: " + seed);
            PointOfPlay pop = parseMove(mv);
            boardView.move(pop, seed, moveNum);

            // we got a move
          } else if (gameOverMatcher.matches()){
            System.out.println("EVENT: Game over");

            String seed = gameOverMatcher.group(1);

            System.out.println("Seed: " + seed);

            boardView.finishBoard(seed);

            setInProgress(false);
            // we got something other than a move, therefore the game is over
            // setting this to false causes the game to be saved to disk.
          } else if (scoreMatcher.matches()) {
            System.out.println("EVENT: Score");

            String score = scoreMatcher.group(1);

            message("Result: " + score);
          } else if (errorMatcher.matches()) {

            System.out.println("EVENT: ERROR");

            boardView.reset();
            setInProgress(false);
          }
        }
      } catch (IllegalMoveException e) {
        message("Illegal move attempted:" + e.getProposedMove());
        message("Position:");
        message(e.getPosition().toString());
      } catch (Exception e) {
        message("oh noes!!!");
        e.printStackTrace();
      }

    });
  }

  static private int priority(String gameType) {
    if(gameType.equals("match")) {
      return 60;
    } else if (gameType.equals("selfplay")) {
      return 50;
    }

    return 0;
  }

  public void message(String x) {
    System.out.println(x);
    setMessage(x + "\n");
  }

  private String nextLine(StringBuffer buff) {
    String firstLine = getFirstLine(buff);

    if(firstLine == null) {
      return null;
    }

    System.out.println("=======================================================================\nLINE: " + firstLine);

    buff.delete(0, firstLine.length());

    return firstLine;

    // Matcher m = EVENT.matcher(firstLine);
    //
    // if (m.matches()) {
    //   String evt = m.group(1);
    //   return evt;
    // }
    // return null;
  }

  private String getFirstLine(StringBuffer buff) {
      int newlineIndex = buff.indexOf("\n");
      if (newlineIndex != -1) {
        return buff.substring(0, newlineIndex + 1);
      }

      return null;
  }

  PointOfPlay parseMove(String move) {
    Matcher m = MOVE.matcher(move);
    if (!m.matches()) {
      throw new RuntimeException("BAD MOVE: " + move);
    }
    String xChar = m.group(1);
    if ("pass".equals(m.group(3)) || "resign".equals(m.group(4))) {
      return null;
    }
    String yNum = m.group(2);
    int x = xChar.toLowerCase().charAt(0) - 'a';
    // sadly leela doesn't output SGF coordinates, so I is omitted
    if (x > 8) {
      x--;
    }
    int y = Integer.valueOf(yNum) - 1;
    return new PointOfPlay(x, y);
  }

  private static Board.Type parseType(String typeStr) {
    if(typeStr.equals("selfplay")) {
      return Board.Type.selfplay;
    } else if (typeStr.equals("match")) {
      return Board.Type.match;
    }

    return null;
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    this.support.addPropertyChangeListener(listener);
  }


  @SuppressWarnings("WeakerAccess")
  public boolean isInProgress() {
    return inProgress;
  }

  @SuppressWarnings("WeakerAccess")
  public void setInProgress(boolean inProgress) {
    boolean old = this.inProgress;
    this.inProgress = inProgress;
    support.firePropertyChange("inProgress", old, inProgress);
  }
}
