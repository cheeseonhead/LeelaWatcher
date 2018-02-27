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

    @Override
    public String toString() {
        return "Board: " + seed;
    }
}
