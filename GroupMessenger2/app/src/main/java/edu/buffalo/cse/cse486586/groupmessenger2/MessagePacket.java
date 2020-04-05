package edu.buffalo.cse.cse486586.groupmessenger2;

public class MessagePacket
{
    private String message;
    private int sequenceNo;
    private String myPort;
    private Boolean isReady;
    public MessagePacket(String msg, int sno, String port, Boolean isReady)
    {
        this.message = msg;
        this.sequenceNo = sno;
        this.myPort = port;
        this.isReady = isReady;
    }

    public String getMessage()
    {
       return this.message;
    }

    public void setMessage(String msg)
    {
        this.message = msg;
    }

    public int getSequenceNo()
    {
        return this.sequenceNo;
    }

    public void setSequenceNo(int sno)
    {
        this.sequenceNo = sno;
    }

    public String getMyPort()
    {
        return this.myPort;
    }

    public void setMyPort(String port)
    {
        this.myPort = port;
    }

    public Boolean getIsReady()
    {
        return this.isReady;
    }

    public void setIsReady(Boolean isReady)
    {
        this.isReady = isReady;
    }
}


