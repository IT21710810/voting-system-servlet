package services;

import com.google.gson.Gson;
import database.DatabaseUtil;
import model.Party;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/parties")
public class PartyServlet extends HttpServlet {
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            int districtId = parseIntOrThrow(request.getParameter("districtId"), "District ID");
            if (!districtExists(districtId)) {
                handleError(response, "District with ID " + districtId + " not found");
                return;
            }

            String[] partyNames = request.getParameterValues("parties[]");
            if (partyNames == null || partyNames.length == 0) {
                handleError(response, "At least one party name is required");
                return;
            }

            List<Party> parties = createParties(partyNames);
            savePartiesToDatabase(districtId, parties);

            response.setContentType("application/json");
            response.getWriter().write(gson.toJson(parties));
        } catch (Exception e) {
            handleError(response, "Failed to create parties: " + e.getMessage());
        }
    }

    private boolean districtExists(int districtId) throws IOException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String sql = "SELECT 1 FROM districts WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, districtId);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            throw new IOException("Database error: " + e.getMessage());
        }
    }

    private List<Party> createParties(String[] partyNames) throws IllegalArgumentException {
        List<Party> parties = new ArrayList<>();
        for (String name : partyNames) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Party name cannot be empty");
            }
            Party party = new Party();
            party.setName(name.trim());
            parties.add(party);
        }
        return parties;
    }

    private void savePartiesToDatabase(int districtId, List<Party> parties) throws IOException {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String sql = "INSERT INTO parties (district_id, name, votes, qualified, first_round_seats, second_round_seats, bonus_seat, total_seats) VALUES (?, ?, 0, false, 0, 0, 0, 0)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                for (Party party : parties) {
                    stmt.setInt(1, districtId);
                    stmt.setString(2, party.getName());
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) party.setId(rs.getInt(1));
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Database error: " + e.getMessage());
        }
    }

    private int parseIntOrThrow(String value, String fieldName) throws IllegalArgumentException {
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