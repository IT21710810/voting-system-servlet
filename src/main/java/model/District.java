package model;

import java.util.ArrayList;
import java.util.List;

public class District {
    private int id;
    private String name;
    private int seats;
    private int totalVotes;
    private int validVotes;
    private int disqualifiedVotes;
    private int voteThreshold;
    private List<Party> parties;

    public District() {
        this.parties = new ArrayList<>();
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSeats() { return seats; }
    public void setSeats(int seats) { this.seats = seats; }

    public int getTotalVotes() { return totalVotes; }
    public void setTotalVotes(int totalVotes) { this.totalVotes = totalVotes; }

    public int getValidVotes() { return validVotes; }
    public void setValidVotes(int validVotes) { this.validVotes = validVotes; }

    public int getDisqualifiedVotes() { return disqualifiedVotes; }
    public void setDisqualifiedVotes(int disqualifiedVotes) {
        this.disqualifiedVotes = disqualifiedVotes;
    }

    public int getVoteThreshold() { return voteThreshold; }
    public void setVoteThreshold(int voteThreshold) {
        this.voteThreshold = voteThreshold;
    }

    public List<Party> getParties() { return parties; }
    public void setParties(List<Party> parties) { this.parties = parties; }

    public void addParty(Party party) { this.parties.add(party); }
}

