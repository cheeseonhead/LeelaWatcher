package leelawatcher.gui;

import leelawatcher.goboard.Board;

public class BoardViewModel {

    public enum Type {
        match("match"),
        selfplay("selfplay");

        Type(String str) {     // Constructor
            this.str = str;
        }

        public String getStr() {
            return str;
        }

        private final String str;
    }

    private Type type;
    private String seed;
    private Board board;
    private String score;

    BoardViewModel(String seed, Type type) {
        this.type = type;
        this.seed = seed;
        this.board = new Board();
    }

    public Type getType() {
        return type;
    }

    public String getSeed() {
        return seed;
    }

    public Board getBoard() {
        return board;
    }


    public String getScore() {
        return score;
    }

    public void setScore(String score) {
        this.score = score;
    }

    public String gameInfo() {
        String resp = type.getStr() + " Move: " + board.getMoveNum() + " Seed: " + seed;
        if(score != null) {
            resp +=  " Result: " + score;
        }

        return resp;
    }

    @Override
    public String toString() {
        return "Board: " + seed;
    }
}
