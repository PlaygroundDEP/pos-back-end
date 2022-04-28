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
import java.sql.SQLException;

@WebServlet(name = "CustomerServlet", value = {"/customers/*"})
public class CustomerServlet extends HttpServlet {

    @Resource(name = "java:comp/env/jdbc/pool4pos")
    private volatile DataSource pool;

    private void doSaveOrUpdate(HttpServletRequest req, HttpServletResponse res) throws IOException {
        System.out.println("jbcj");
        if (req.getContentType() == null || !req.getContentType().toLowerCase().startsWith("application/json")) {
            res.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        String method = req.getMethod();
        String pathInfo = req.getPathInfo();

        if (method.equals("POST") &&
                !((req.getServletPath().equalsIgnoreCase("/customers") ||
                        req.getServletPath().equalsIgnoreCase("/customers/")))){
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            Jsonb jsonb = JsonbBuilder.create();
            CustomerDTO customer = jsonb.fromJson(req.getReader(), CustomerDTO.class);
            if (method.equals("POST")) {
                if (customer.getNic() == null || !customer.getNic().matches("\\d{9}[Vv]")) {
                    throw new ValidationException("Invalid NIC");
                } else if (customer.getName() == null || !(customer.getName().matches("[A-Za-z ]+"))) {
                    throw new ValidationException("Invalid Name");
                } else if (customer.getAddress() == null || !(customer.getAddress().matches("[A-Za-z ]+"))) {
                    throw new ValidationException("Invalid Address");
                } else if (customer.getContact() == null || !customer.getContact().matches("\\d{3}-\\d{7}")) {
                    throw new ValidationException("Invalid Contact Number");
                }
            }

            try (Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("SELECT * FROM customer WHERE nic=?");
                stm.setString(1, customer.getNic());
                if (stm.executeQuery().next()) {
                    if (method.equals("POST")) {
                        res.sendError(HttpServletResponse.SC_CONFLICT, "Customer already exists");
                    } else {
                    }
                } else {
                    stm = connection.prepareStatement("INSERT INTO customer (nic, name, address, contact) VALUES (?,?,?,?)");
                    stm.setString(1, customer.getNic());
                    stm.setString(2, customer.getName());
                    stm.setString(3, customer.getAddress());
                    stm.setString(4, customer.getContact());

                    if (stm.executeUpdate()!=1) {
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
        doSaveOrUpdate(req,resp);
    }
}
