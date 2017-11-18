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

package tsb.scorer;

import tsb.goboard.*;

import java.util.HashSet;
import java.util.Iterator;

public class QuickRules extends AbstractRules {
  public QuickRules(Board aBoard) {
    super(aBoard);
  }

  public boolean isLegalMove(PointOfPlay p) {
    if (p.getX() == Move.PASS) {
      return true;  // it is always legal to pass
    }
    return (isEmpty(p) && !isSelfCapture(p) && !isKo(p));
  }

  public boolean isSelfCapture(PointOfPlay p) {
    Move tmproot = new Move();
    char color = Move.MOVE_BLACK;
    boolean captureOpponent = false;  // at least until otherwise determined

    PointOfPlay neighbor;
    Move testMove;
    Position tmpPos;
    MarkablePosition testMPos;

    // we must build a MarkablePosition that shows the board as it would be
    // if the stone were placed in order to test if this would result in self
    // capture (illegal in most rules of the game)

    if (aBoard.isWhiteMove()) {
      color = Move.MOVE_WHITE;
    }
    testMove = new Move(p.getX(), p.getY(), color, tmproot);
    tmpPos = new Position(aBoard.getCurrPos(), testMove);
    testMPos = new MarkablePosition(tmpPos);
    //testMPos.dPrint();

    //System.out.println(captureOpponent);
    neighbor = new PointOfPlay(p.getX(), p.getY() + 1);
    captureOpponent = (captureOpponent
        || ((aBoard.isOnBoard(neighbor)
        && (countLibs(neighbor, 0, testMPos) == 0))
        && (testMPos.colorAt(p)
        != testMPos.colorAt(neighbor))));
    testMPos.clearMarks();
    neighbor = new PointOfPlay(p.getX() + 1, p.getY());
    //System.out.println(captureOpponent);
    captureOpponent = (captureOpponent
        || ((aBoard.isOnBoard(neighbor)
        && (countLibs(neighbor, 0, testMPos) == 0))
        && (testMPos.colorAt(p)
        != testMPos.colorAt(neighbor))));
    testMPos.clearMarks();
    neighbor = new PointOfPlay(p.getX(), p.getY() - 1);
    //System.out.println(captureOpponent);
    captureOpponent = (captureOpponent
        || ((aBoard.isOnBoard(neighbor)
        && (countLibs(neighbor, 0, testMPos) == 0))
        && (testMPos.colorAt(p)
        != testMPos.colorAt(neighbor))));
    testMPos.clearMarks();
    neighbor = new PointOfPlay(p.getX() - 1, p.getY());
    //System.out.println(captureOpponent);
    captureOpponent = (captureOpponent
        || ((aBoard.isOnBoard(neighbor)
        && (countLibs(neighbor, 0, testMPos) == 0))
        && (testMPos.colorAt(p)
        != testMPos.colorAt(neighbor))));
    testMPos.clearMarks();
    //testMPos.dPrint();
    //System.out.println(countLibs(p,0,testMPos));
    //testMPos.clearMarks();
    //System.out.println(captureOpponent);
    return (countLibs(p, 0, testMPos) == 0) && !captureOpponent;
  }

  public boolean isKo(PointOfPlay p) {
    Move tmproot = new Move();
    int stonesRemoved = 0;
    char color = Move.MOVE_BLACK;
    boolean repeated = false;  // at least until otherwise determined

    PointOfPlay neighbor;
    Move testMove;
    Position tmpPos;
    MarkablePosition testMPos;

    // we must build a MarkablePosition that shows the board as it would be
    // if the stone were placed in order to test if this would result in self
    // capture (illegal in most rules of the game)

    if (aBoard.isWhiteMove()) {
      color = Move.MOVE_WHITE;
    }
    testMove = new Move(p.getX(), p.getY(), color, tmproot);
    tmpPos = new Position(aBoard.getCurrPos(), testMove);
    testMPos = new MarkablePosition(tmpPos);

    neighbor = new PointOfPlay(p.getX(), p.getY() + 1);
    if (aBoard.isOnBoard(neighbor)
        && (countLibs(neighbor, 0, testMPos) == 0)
        && (testMPos.getGroupSet(neighbor,
        (HashSet) null,
        aBoard.getBoardSize()).size() == 1)) {
      //System.out.println("rem + y");
      ++stonesRemoved;
      testMPos.removeStoneAt(neighbor);
    }

    neighbor = new PointOfPlay(p.getX() + 1, p.getY());
    if (aBoard.isOnBoard(neighbor)
        && (countLibs(neighbor, 0, testMPos) == 0)
        && (testMPos.getGroupSet(neighbor,
        (HashSet) null,
        aBoard.getBoardSize()).size() == 1)) {
      //System.out.println("rem + x");
      ++stonesRemoved;
      testMPos.removeStoneAt(neighbor);
    }
    neighbor = new PointOfPlay(p.getX(), p.getY() - 1);
    if (aBoard.isOnBoard(neighbor)
        && (countLibs(neighbor, 0, testMPos) == 0)
        && (testMPos.getGroupSet(neighbor,
        (HashSet) null,
        aBoard.getBoardSize()).size() == 1)) {
      //System.out.println("rem - y");
      ++stonesRemoved;
      testMPos.removeStoneAt(neighbor);
    }
    neighbor = new PointOfPlay(p.getX() - 1, p.getY());
    if (aBoard.isOnBoard(neighbor)
        && (countLibs(neighbor, 0, testMPos) == 0)
        && (testMPos.getGroupSet(neighbor,
        (HashSet) null,
        aBoard.getBoardSize()).size() == 1)) {
      //System.out.println("rem - x");
      ++stonesRemoved;
      testMPos.removeStoneAt(neighbor);
    }

    //System.out.println(stonesRemoved);
    if (stonesRemoved == 1) {
      int x = 0;

      for (Iterator i = aBoard.getPosIter(); i.hasNext(); ) {
        ++x;
        if (((Position) i.next()).equals(testMPos)) {
          return true;
        }
      }
    }
    return false;
  }
}


/*
 * $Log$
 * Revision 1.6  2003/10/10 00:41:17  gus
 * updating the name of the position class
 *
 * Revision 1.5  2003/07/19 03:41:46  gus
 * comment out debugging prints
 *
 * Revision 1.4  2003/07/19 02:50:05  gus
 * New License based on the Apache License, Yeah open source :)
 *
 * Revision 1.3  2002/12/26 04:33:48  gus
 * Make pass a legal move
 *
 * Revision 1.2  2002/12/16 06:35:49  gus
 * Fixes that squash a bug that allowed ko to be violated
 * in certain cases.
 *
 * Revision 1.1.1.1  2002/12/15 07:02:57  gus
 * Initial import into cvs server running on Aptiva
 *
 * Revision 1.2  2002/02/27 04:08:23  togo
 * Added javadoc, and variable naming scheme to tsb.GoBoard board.java and
 * renamed it tsb.GoBoard.Board.java.
 *
 */

