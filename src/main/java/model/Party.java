package model;

public class Party {
    private int id;
    private String name;
    private int votes;
    private boolean qualified;
    private int firstRoundSeats;
    private int secondRoundSeats;
    private int bonusSeat;
    private int totalSeats;

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getVotes() { return votes; }
    public void setVotes(int votes) { this.votes = votes; }

    public boolean isQualified() { return qualified; }
    public void setQualified(boolean qualified) { this.qualified = qualified; }

    public int getFirstRoundSeats() { return firstRoundSeats; }
    public void setFirstRoundSeats(int seats) { this.firstRoundSeats = seats; }

    public int getSecondRoundSeats() { return secondRoundSeats; }
    public void setSecondRoundSeats(int seats) { this.secondRoundSeats = seats; }

    public int getBonusSeat() { return bonusSeat; }
    public void setBonusSeat(int seat) { this.bonusSeat = seat; }

    public int getTotalSeats() { return totalSeats; }
    public void setTotalSeats(int seats) { this.totalSeats = seats; }
}
