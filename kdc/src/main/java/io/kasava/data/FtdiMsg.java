package io.kasava.data;

public class FtdiMsg {
    public String msg;
    public int attempts;
    public boolean getsReply;

    public FtdiMsg (String msg, int attempts, boolean getsReply) {
        this.msg = msg;
        this.attempts = attempts;
        this.getsReply = getsReply;
    }
}
