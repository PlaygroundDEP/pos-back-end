package lk.ijse.dep8.pos.api;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import lk.ijse.dep8.pos.dto.CustomerDTO;
import lk.ijse.dep8.pos.exception.ValidationException;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet(name = "CustomerServlet", value = {"/customers/*"})
public class CustomerServlet extends HttpServlet {

    @Resource(name = "java:comp/env/jdbc/pool4pos")
    private volatile DataSource pool;

    private void doSaveOrUpdate(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (req.getContentType() == null || !req.getContentType().toLowerCase().startsWith("application/json")) {
            res.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        String method = req.getMethod();
        String pathInfo = req.getPathInfo();

        if (method.equals("POST") &&
                !((req.getServletPath().equalsIgnoreCase("/customers") ||
                        req.getServletPath().equalsIgnoreCase("/customers/")))) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        } else if (method.equals("PUT") && !(pathInfo != null &&
                pathInfo.substring(1).matches("\\d{9}[Vv][/]?"))) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Member does not exist");
            return;
        }

        try {
            Jsonb jsonb = JsonbBuilder.create();
            CustomerDTO customer = jsonb.fromJson(req.getReader(), CustomerDTO.class);
            if (method.equals("POST") &&
                    (customer.getNic() == null || !customer.getNic().matches("\\d{9}[Vv]"))) {
                throw new ValidationException("Invalid NIC");
            } else if (customer.getName() == null || !(customer.getName().matches("[A-Za-z ]+"))) {
                throw new ValidationException("Invalid Name");
            } else if (customer.getAddress() == null || !(customer.getAddress().matches("[A-Za-z ]+"))) {
                throw new ValidationException("Invalid Address");
            } else if (customer.getContact() == null || !customer.getContact().matches("\\d{3}-\\d{7}")) {
                throw new ValidationException("Invalid contact number");
            } else if (method.equals("PUT")) {
                customer.setNic(pathInfo.replaceAll("[/]", ""));
            }

            try (Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("SELECT * FROM customer WHERE nic=?");
                stm.setString(1, customer.getNic());
                if (stm.executeQuery().next()) {
                    if (method.equals("POST")) {
                        res.sendError(HttpServletResponse.SC_CONFLICT, "Customer already exists");
                    } else {
                        stm = connection.prepareStatement("UPDATE customer SET name=?, address=?, contact=? WHERE nic=?");
                        stm.setString(1, customer.getName());
                        stm.setString(2, customer.getAddress());
                        stm.setString(3, customer.getContact());
                        stm.setString(4, customer.getNic());
                        if (stm.executeUpdate() != 1) {
                            throw new RuntimeException("Failed to update the member");
                        }
                        res.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                } else {
                    stm = connection.prepareStatement("INSERT INTO customer (nic, name, address, contact) VALUES (?,?,?,?)");
                    stm.setString(1, customer.getNic());
                    stm.setString(2, customer.getName());
                    stm.setString(3, customer.getAddress());
                    stm.setString(4, customer.getContact());

                    if (stm.executeUpdate() != 1) {
                        throw new RuntimeException("Unable to save the customer");
                    } else {
                        res.sendError(HttpServletResponse.SC_CREATED);
                    }
                }
            }
        } catch (JsonbException e) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Json");
        } catch (Throwable t) {
            t.printStackTrace();
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doSaveOrUpdate(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doSaveOrUpdate(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo() == null || req.getPathInfo().equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Unable to delete all the customers");
            return;
        } else if (req.getPathInfo() != null && !req.getPathInfo().substring(1).matches("\\d{9}[Vv][/]?")) {
            System.out.println("h");
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Customer not found");
            return;
        }

        String nic = req.getPathInfo().replaceAll("[/]", "");

        try (Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM customer WHERE nic=?");
            stm.setString(1, nic);
            if (stm.executeQuery().next()) {
                stm = connection.prepareStatement("DELETE FROM customer WHERE nic=?");
                stm.setString(1, nic);
                if (stm.executeUpdate() != 1) {
                    throw new RuntimeException("Failed to delete the customer");
                }
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Customer not found");
            }
        } catch (SQLException | RuntimeException e) {
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo() != null && !req.getPathInfo().equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }


        String query = req.getParameter("q");
        query = "%" + ((query == null) ? "" : query) + "%";

        try (Connection connection = pool.getConnection()) {
            boolean pagination = req.getParameter("page") != null && req.getParameter("size") != null;

            PreparedStatement stm = connection.prepareStatement("SELECT * FROM customer WHERE nic LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?" +
                    ((pagination) ? "LIMIT ? OFFSET ?" : ""));

            PreparedStatement stmCount = connection.prepareStatement("SELECT count(*) FROM customer WHERE nic LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?");

            stm.setString(1, query);
            stm.setString(2, query);
            stm.setString(3, query);
            stm.setString(4, query);
            stmCount.setString(1, query);
            stmCount.setString(2, query);
            stmCount.setString(3, query);
            stmCount.setString(4, query);

            if (pagination) {
                int page = Integer.parseInt(req.getParameter("page"));
                int size = Integer.parseInt(req.getParameter("size"));
                stm.setInt(5, size);
                stm.setInt(6, (page - 1) * size);
            }
            ResultSet rst = stm.executeQuery();

            List<CustomerDTO> customers = new ArrayList<>();

            while (rst.next()) {
                customers.add(new CustomerDTO(
                        rst.getString("nic"),
                        rst.getString("name"),
                        rst.getString("address"),
                        rst.getString("contact")
                ));
            }

            resp.setContentType("application/json");

            ResultSet rstCount = stmCount.executeQuery();
            if (rst.next()) {
                resp.setHeader("X-Count", rstCount.getString(1));
            }

            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(customers, resp.getWriter());


        } catch (SQLException e) {
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
