package services;

import com.google.gson.Gson;
import database.DatabaseUtil;
import model.District;
import model.Party;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

@WebServlet("/api/votes")
public class VoteServlet extends HttpServlet {
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            int districtId = parseIntOrThrow(request.getParameter("districtId"), "District ID");
            District district = fetchDistrictById(districtId);
            if (district == null) {
                handleError(response, "District with ID " + districtId + " not found");
                return;
            }

            int totalVotes = parseIntOrThrow(request.getParameter("totalVotes"), "Total Votes");
            district.setTotalVotes(totalVotes);

            updatePartyVotes(request.getParameterMap(), district);
            calculateResults(district);
            updateDistrictInDatabase(district);

            response.setContentType("application/json");
            response.getWriter().write(gson.toJson(district));
        } catch (Exception e) {
            handleError(response, "Failed to process votes: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            int districtId = parseIntOrThrow(request.getParameter("districtId"), "District ID");
            District district = fetchDistrictById(districtId);
            if (district == null) {
                handleError(response, "District with ID " + districtId + " not found");
                return;
            }
            response.setContentType("application/json");
            response.getWriter().write(gson.toJson(district));
        } catch (Exception e) {
            handleError(response, "Failed to fetch district: " + e.getMessage());
        }
    }

    private District fetchDistrictById(int id) throws IOException {
        District district = null;
        try (Connection conn = DatabaseUtil.getConnection()) {
            String sql = "SELECT * FROM districts WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        district = new District();
                        district.setId(rs.getInt("id"));
                        district.setName(rs.getString("name"));
                        district.setSeats(rs.getInt("seats"));
                        district.setTotalVotes(rs.getInt("total_votes"));
                        district.setValidVotes(rs.getInt("valid_votes"));
                        district.setDisqualifiedVotes(rs.getInt("disqualified_votes"));
                        district.setVoteThreshold(rs.getInt("vote_threshold"));
                    }
                }
            }

            if (district != null) {
                district.setParties(fetchPartiesByDistrictId(id, conn));
            }
        } catch (Exception e) {
            throw new IOException("Database error: " + e.getMessage());
        }
        return district;
    }

    private List<Party> fetchPartiesByDistrictId(int districtId, Connection conn) throws IOException {
        List<Party> parties = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM parties WHERE district_id = ?")) {
            stmt.setInt(1, districtId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Party party = new Party();
                    party.setId(rs.getInt("id"));
                    party.setName(rs.getString("name"));
                    party.setVotes(rs.getInt("votes"));
                    party.setQualified(rs.getBoolean("qualified"));
                    party.setFirstRoundSeats(rs.getInt("first_round_seats"));
                    party.setSecondRoundSeats(rs.getInt("second_round_seats"));
                    party.setBonusSeat(rs.getInt("bonus_seat"));
                    party.setTotalSeats(rs.getInt("total_seats"));
                    parties.add(party);
                }
            }
        } catch (Exception e) {
            throw new IOException("Database error: " + e.getMessage());
        }
        return parties;
    }

    private void updatePartyVotes(Map<String, String[]> parameterMap, District district) throws IllegalArgumentException {
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String partyName = entry.getKey().trim();
            String[] votesStr = entry.getValue();
            if (partyName.equals("districtId") || partyName.equals("totalVotes") || votesStr == null || votesStr.length == 0) {
                continue;
            }

            int votes = parseIntOrThrow(votesStr[0], "Votes for " + partyName);
            Party party = findPartyByName(district.getParties(), partyName);
            if (party != null) {
                party.setVotes(votes);
            } else {
                throw new IllegalArgumentException("Party " + partyName + " not found in district");
            }
        }
    }

    private void calculateResults(District district) {
        int totalVotes = district.getTotalVotes();
        district.setValidVotes(0);
        district.setDisqualifiedVotes(totalVotes);
        district.setVoteThreshold((int) (totalVotes * 0.0625)); // 6.25% threshold to disqualify 500 votes

        if (totalVotes <= 0) {
            district.getParties().forEach(p -> {
                p.setQualified(false);
                p.setFirstRoundSeats(0);
                p.setSecondRoundSeats(0);
                p.setBonusSeat(0);
                p.setTotalSeats(0);
            });
            return;
        }

        // Use strict > comparison for qualification
        List<Party> qualifiedParties = district.getParties().stream()
                .filter(p -> p.getVotes() > district.getVoteThreshold())
                .collect(Collectors.toList());

        if (qualifiedParties.isEmpty()) {
            district.getParties().forEach(p -> {
                p.setQualified(false);
                p.setFirstRoundSeats(0);
                p.setSecondRoundSeats(0);
                p.setBonusSeat(0);
                p.setTotalSeats(0);
            });
            return;
        }

        int totalValidVotes = qualifiedParties.stream().mapToInt(Party::getVotes).sum();
        district.setValidVotes(totalValidVotes);
        district.setDisqualifiedVotes(totalVotes - totalValidVotes);

        int totalSeats = district.getSeats();
        int seatsForProportional = totalSeats - 1; // Reserve 1 seat for bonus
        int[] seatAllocation = new int[qualifiedParties.size()];

        // Proportional allocation for remaining seats
        for (int i = 0; i < qualifiedParties.size(); i++) {
            Party party = qualifiedParties.get(i);
            seatAllocation[i] = (int) Math.round((double) party.getVotes() / totalValidVotes * seatsForProportional);
            party.setFirstRoundSeats(seatAllocation[i]);
            party.setTotalSeats(seatAllocation[i]);
            party.setQualified(true);
        }

        int allocatedSeats = Arrays.stream(seatAllocation).sum();
        int remainingSeats = seatsForProportional - allocatedSeats;

        if (remainingSeats > 0) {
            Party maxParty = qualifiedParties.stream()
                    .max(Comparator.comparingInt(Party::getVotes))
                    .orElse(qualifiedParties.get(0));
            maxParty.setFirstRoundSeats(maxParty.getFirstRoundSeats() + remainingSeats);
            maxParty.setTotalSeats(maxParty.getTotalSeats() + remainingSeats);
        }

        // Award bonus seat to the party with the most votes
        Party bonusParty = qualifiedParties.stream()
                .max(Comparator.comparingInt(Party::getVotes))
                .orElse(qualifiedParties.get(0));
        bonusParty.setBonusSeat(1);
        bonusParty.setTotalSeats(bonusParty.getTotalSeats() + 1);

        // Adjust to ensure total seats = 10
        int totalAllocatedSeats = qualifiedParties.stream().mapToInt(Party::getTotalSeats).sum();
        if (totalAllocatedSeats > totalSeats) {
            // Reduce seats from the party with the least votes (but not the bonus party)
            Party minParty = qualifiedParties.stream()
                    .filter(p -> p != bonusParty)
                    .min(Comparator.comparingInt(Party::getVotes))
                    .orElse(qualifiedParties.get(qualifiedParties.size() - 1));
            int excessSeats = totalAllocatedSeats - totalSeats;
            minParty.setFirstRoundSeats(minParty.getFirstRoundSeats() - excessSeats);
            minParty.setTotalSeats(minParty.getTotalSeats() - excessSeats);
        }

        district.getParties().stream()
                .filter(p -> !qualifiedParties.contains(p))
                .forEach(p -> {
                    p.setQualified(false);
                    p.setFirstRoundSeats(0);
                    p.setSecondRoundSeats(0);
                    p.setBonusSeat(0);
                    p.setTotalSeats(0);
                });
    }

    private void updateDistrictInDatabase(District district) throws IOException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String districtSql = "UPDATE districts SET total_votes = ?, valid_votes = ?, disqualified_votes = ?, vote_threshold = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(districtSql)) {
                stmt.setInt(1, district.getTotalVotes());
                stmt.setInt(2, district.getValidVotes());
                stmt.setInt(3, district.getDisqualifiedVotes());
                stmt.setInt(4, district.getVoteThreshold());
                stmt.setInt(5, district.getId());
                stmt.executeUpdate();
            }

            String partySql = "UPDATE parties SET votes = ?, qualified = ?, first_round_seats = ?, second_round_seats = ?, bonus_seat = ?, total_seats = ? WHERE district_id = ? AND id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(partySql)) {
                for (Party party : district.getParties()) {
                    stmt.setInt(1, party.getVotes());
                    stmt.setBoolean(2, party.isQualified());
                    stmt.setInt(3, party.getFirstRoundSeats());
                    stmt.setInt(4, party.getSecondRoundSeats());
                    stmt.setInt(5, party.getBonusSeat());
                    stmt.setInt(6, party.getTotalSeats());
                    stmt.setInt(7, district.getId());
                    stmt.setInt(8, party.getId());
                    stmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            throw new IOException("Database error: " + e.getMessage());
        }
    }

    private Party findPartyByName(List<Party> parties, String partyName) {
        return parties.stream()
                .filter(p -> p.getName().equals(partyName))
                .findFirst()
                .orElse(null);
    }

    private int parseIntOrThrow(String value, String fieldName) throws IllegalArgumentException {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid integer");
        }
    }

    private void handleError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write("Error: " + message);
    }
}