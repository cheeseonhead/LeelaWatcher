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
  private static final Pattern LINE =
          Pattern.compile("^(.*)$", Pattern.MULTILINE);

  private static final Pattern MOVE = Pattern.compile("(?:(.)(\\d+))|(pass)|(resign)");
  private BoardView boardView;
  private boolean inProgress = false;
  private static String currentPlayingSeed = "";

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
          String event = nextEvent(buffer);
          if (event == null) {
            continue;
          }
          Matcher m = MOVE_EVENT.matcher(event);
          if (m.matches()) {

            String seed = m.group(1);
            String moveNum = m.group(2);

            if (moveNum.equals("1") && currentPlayingSeed.equals("")) {
              currentPlayingSeed = seed;
            }

            if(!currentPlayingSeed.equals(seed)) {
              message("Got move " + moveNum + " for game: " + seed + ", not playing.\n");
              continue;
            }

            if (!isInProgress()) {
              boardView.reset();
              System.out.println();
              message("New Game: " + seed + " Started!\n");
            }

            setInProgress(true);
            String mv = m.group(3);
            System.out.print(" \t");
            message("Playing move " + moveNum + " " + mv + " game: " + seed + "\n");
            PointOfPlay pop = parseMove(mv);
            boardView.move(pop);
            // we got a move
          } else {
            Matcher ggm = GAMEOVER_EVENT.matcher(event);
            if (ggm.matches()) {
              String seed = ggm.group(1);
              if (seed.equals(currentPlayingSeed)) {
                currentPlayingSeed = "";
                message("Finished showing game: " + seed);
                setInProgress(false);
              }
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

  private void message(String x) {
    System.out.println(x);
    setMessage(x + "\n");
  }

  private String nextEvent(StringBuffer buff) {
    String firstLine = getFirstLine(buff);

    if(firstLine == null) {
      return null;
    }

    System.out.println("======= GOT LINE: " + firstLine);

    buff.delete(0, firstLine.length());

    Matcher m = EVENT.matcher(firstLine);

    if (m.matches()) {
      String evt = m.group(1);
      return evt;
    }
    return null;
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
