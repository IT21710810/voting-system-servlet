package services;

import com.google.gson.Gson;
import database.DatabaseUtil;
import model.District;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/district")
public class DistrictServlet extends HttpServlet {
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            District district = new District();
            district.setName(request.getParameter("name"));
            district.setSeats(parseIntOrThrow(request.getParameter("seats"), "Seats"));

            saveDistrict(district);
            request.getSession().setAttribute("district", district);

            response.setContentType("application/json");
            response.getWriter().write(gson.toJson(district));
        } catch (Exception e) {
            handleError(response, "Failed to create district: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<District> districts = fetchAllDistricts();
            if (districts.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType("application/json");
            response.getWriter().write(gson.toJson(districts));
        } catch (Exception e) {
            handleError(response, "Failed to fetch districts: " + e.getMessage());
        }
    }

    private void saveDistrict(District district) throws Exception {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String sql = "INSERT INTO districts (name, seats) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, district.getName());
                stmt.setInt(2, district.getSeats());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) district.setId(rs.getInt(1));
                }
            }
        }
    }

    private List<District> fetchAllDistricts() throws Exception {
        List<District> districts = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection()) {
            String sql = "SELECT * FROM districts";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    District district = new District();
                    district.setId(rs.getInt("id"));
                    district.setName(rs.getString("name"));
                    district.setSeats(rs.getInt("seats"));
                    district.setTotalVotes(rs.getInt("total_votes"));
                    district.setValidVotes(rs.getInt("valid_votes"));
                    district.setDisqualifiedVotes(rs.getInt("disqualified_votes"));
                    district.setVoteThreshold(rs.getInt("vote_threshold"));
                    districts.add(district);
                }
            }
        }
        return districts;
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