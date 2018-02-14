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

import leelawatcher.goboard.IllegalMoveException;
import leelawatcher.goboard.PointOfPlay;
import leelawatcher.gui.BoardView;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoGtpOutputParser {

  /*
   * This pattern is meant to report a match for one of 3 groups:
   * 1. anything ending in 'set.'
   * (?:[BW]\\s)? is added so that both AutoGTPv11 outputs (B A1) (W F18) and AutoGTPv9 outputs (A1) (F18) will work.
   */
  private static final Pattern EVENT =
          Pattern.compile("^(.*set\\.|\\s*\\w+\\s\\d+\\s\\((?:[BW]\\s)?\\w+\\)\\s*|\\s*\\w+\\sGame\\shas\\sended.\\s*).*");
  private static final Pattern MOVE_EVENT =
          Pattern.compile("\\s*(\\w+)\\s(\\d+)\\s\\((?:[BW]\\s)?(\\w+)\\)\\s*");
  private static final Pattern GAMEOVER_EVENT =
          Pattern.compile("\\s*(\\w+)\\sGame\\shas\\sended.\\s*");
  private static final Pattern GAMESTART_EVENT =
          Pattern.compile("\\s*Got\\snew\\sjob:\\s(\\w+)\\s*");
  private static final Pattern LINE =
          Pattern.compile("^(.*)$", Pattern.MULTILINE);

  private static final Pattern MOVE = Pattern.compile("(?:(.)(\\d+))|(pass)|(resign)");

  private static final int POST_ENDGAME_THRESHOLD = 300;

  private BoardView boardView;
  private boolean inProgress = false;
  private static String currentPlayingSeed = "";
  private static int currentGamePriority = 0; // 0: No Game, 30: selfplay post-endgame, 40: match post-endgame, 50: selfplay, 60: match

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

          if (gameStartMatcher.matches()) {

            System.out.println("EVENT: Game start");

            String gameType = gameStartMatcher.group(1);
            int gamePriority = priority(gameType);

            System.out.println("Type: " + gameType + " Priority: " + gamePriority);

            System.out.println("Comparing with current priority: " + currentGamePriority + "...");
            if(gamePriority > currentGamePriority) {
              currentPlayingSeed = "";
              currentGamePriority = gamePriority;

              setInProgress(false);

              System.out.println("It's higher, clearing currentPlayingSeed. Set current priority to: " + currentGamePriority);
            } else {
              System.out.println("It's lower, do nothing.");
            }
          }
          else if (moveMatcher.matches()) {

            System.out.println("EVENT: Move");

            String seed = moveMatcher.group(1);
            int moveNum = Integer.parseInt(moveMatcher.group(2));
            String mv = moveMatcher.group(3);

            System.out.println("Move Number: " + moveNum + " Location: " + mv + " Seed: " + seed);

            if(moveNum == 1 && currentPlayingSeed.equals("")) {
              currentPlayingSeed = seed;
              System.out.println("It's move 1 and current seed is empty. Set current seed: " + currentPlayingSeed);
            }

            System.out.println("Current seed is: " + currentPlayingSeed);

            if(!currentPlayingSeed.equals(seed)) {
              System.out.println("Seed is not a match, aborting.");
              continue;
            }

            System.out.println("Seed is a match! Processing move...");

            if (!isInProgress()) {
              boardView.reset();
              // message("Game: " + seed + " Started!\n");
            }

            setInProgress(true);
            message("Playing move " + moveNum + " " + mv + " seed: " + seed);
            PointOfPlay pop = parseMove(mv);
            boardView.move(pop);

            System.out.println("Checking current priority: " + currentGamePriority + " and move number: " + moveNum);
            if (currentGamePriority >= 50 && moveNum >= POST_ENDGAME_THRESHOLD) {
              System.out.println("Just entered post-endgame phase, decreasing priority...");
              currentGamePriority -= 20;
              System.out.println("New current priority: " + currentGamePriority);
            }
            // we got a move
          } else if (gameOverMatcher.matches()){

            System.out.println("EVENT: Game over");

            String seed = gameOverMatcher.group(1);

            System.out.println("Seed: " + seed);
            if (seed.equals(currentPlayingSeed)) {
              currentGamePriority = 0;
              System.out.println("Current game has finished. Resetting priority: " + currentGamePriority);
              setInProgress(false);
            } else {
              System.out.println("Not the game we are rendering, do nothing.");
            }
            // we got something other than a move, therefore the game is over
            // setting this to false causes the game to be saved to disk.
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

  private void message(String x) {
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
