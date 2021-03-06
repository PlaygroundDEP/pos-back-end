package lk.ijse.dep8.pos.api;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import lk.ijse.dep8.pos.dto.ItemDTO;
import lk.ijse.dep8.pos.exception.ValidationException;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import javax.sql.DataSource;
import javax.sql.rowset.serial.SerialBlob;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@MultipartConfig(location = "/tmp", maxFileSize = 15 * 1024 * 1024)
@WebServlet(name = "ItemServlet", value = {"/items/*"})
public class ItemServlet extends HttpServlet {



    @Resource(name = "java:comp/env/jdbc/pool4pos")
    private volatile DataSource pool;

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
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Unable to delete all items");
            return;
        } /*else if (req.getPathInfo() != null &&
                !req.getPathInfo().substring(1).matches("")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Item does not exist");
            return;
        }*/

        String id = req.getPathInfo().replaceAll("[/]", "");

        try (Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.
                    prepareStatement("SELECT * FROM item WHERE id=?");
            stm.setString(1, id);
            ResultSet rst = stm.executeQuery();

            if (rst.next()) {
                stm = connection.prepareStatement("DELETE FROM item WHERE id=?");
                stm.setString(1, id);
                if (stm.executeUpdate() != 1) {
                    throw new RuntimeException("Failed to delete the item");
                }
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Item does not exist");
            }
        } catch (SQLException | RuntimeException e) {
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo() != null && !req.getPathInfo().equals("/")){
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
        }

        String query = req.getParameter("q");
        query = "%" + ((query == null) ? "" : query) + "%";

        try (Connection connection = pool.getConnection()) {

            boolean pagination = req.getParameter("page") != null &&
                    req.getParameter("size") != null;
            String sql = "SELECT * FROM item WHERE id LIKE ? OR name LIKE ? OR price LIKE ?" + ((pagination) ? "LIMIT ? OFFSET ?" : "");

            PreparedStatement stm = connection.prepareStatement(sql);
            PreparedStatement stmCount = connection.prepareStatement("SELECT count(*) FROM item WHERE id LIKE ? OR name LIKE ? OR price LIKE ?");

            stm.setString(1, query);
            stm.setString(2, query);
            stm.setString(3, query);
            stmCount.setString(1, query);
            stmCount.setString(2, query);
            stmCount.setString(3, query);

            if (pagination) {
                int page = Integer.parseInt(req.getParameter("page"));
                int size = Integer.parseInt(req.getParameter("size"));
                stm.setInt(4, size);
                stm.setInt(5, (page - 1) * size);
            }
            ResultSet rst = stm.executeQuery();

            List<ItemDTO> books = new ArrayList<>();

            while (rst.next()) {
                books.add((new ItemDTO(
                        rst.getString("id"),
                        rst.getString("name"),
                        rst.getFloat("price"),
                        rst.getInt("available"),
                        rst.getBytes("preview")
                )));
            }

            ResultSet rst2 = stmCount.executeQuery();
            if (rst2.next()) {
                resp.setHeader("X-Count", rst2.getString(1));
            }

            resp.setContentType("application/json");
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(books, resp.getWriter());

        } catch (SQLException t) {
            t.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void doSaveOrUpdate(HttpServletRequest req, HttpServletResponse res) throws IOException {

        if (req.getContentType()==null || req.getContentType().toLowerCase().startsWith("multipart/form-date")) {
            res.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        String method = req.getMethod();
        String pathInfo = req.getPathInfo();

        if (method.equals("POST") && (pathInfo!=null && !pathInfo.equals("/"))) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        } /*else if (method.equals("PUT") && !(pathInfo != null &&
                pathInfo.substring(1).matches("[I]{1}d{3}"))){
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Item does not exist");
            return;
        }*/
        
        try{
            String id = generateId();
            String name = req.getParameter("name");
            float price = Float.parseFloat(req.getParameter("price"));
            int available = Integer.parseInt(req.getParameter("available"));
            Part preview = req.getPart("preview");


            ItemDTO item;

            if (preview!=null && !preview.getSubmittedFileName().isEmpty()){
                if (!preview.getContentType().toLowerCase().startsWith("image/")) {
                    throw new ValidationException("Invalid preview");
                }

                byte[] buffer = new byte[(int) preview.getSize()];
                preview.getInputStream().read(buffer);

                item = new ItemDTO(id, name, price, available, buffer);
            } else {
                item = new ItemDTO(id, name, price, available);
            }

            if (method.equals("POST") && (item.getName() == null || !item.getName().matches(".+"))) {
                throw new ValidationException("Invalid Name");
            } else if (item.getPrice() < 0) {
                throw new ValidationException("Invalid Price");
            } else if (item.getAvailable() <0) {
                throw new ValidationException("Invalid Available Items");
            }

            if (method.equals("PUT")) {
                item.setId(pathInfo.replaceAll("[/]", ""));
            }

            try(Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("SELECT * FROM item WHERE id=?");
                stm.setString(1, item.getId());
                ResultSet rst = stm.executeQuery();

                if (rst.next()) {
                    if (method.equals("POST")) {
                        res.sendError(HttpServletResponse.SC_CONFLICT, "Item already exists");
                    } else {
                        stm = connection.
                                prepareStatement("UPDATE item SET name=?, price=?, available=?, preview=? WHERE id=?");
                        stm.setString(1, item.getName());
                        stm.setFloat(2, item.getPrice());
                        stm.setInt(3, item.getAvailable());
                        stm.setBlob(4, item.getPreview() != null ? new SerialBlob(item.getPreview()) : null);
                        stm.setString(5, item.getId());

                        if (stm.executeUpdate() != 1) {
                            throw new RuntimeException("Failed to update the item details");
                        }
                        res.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                } else {
                    stm = connection.prepareStatement("INSERT INTO item (id, name, price, available, preview) VALUES (?,?,?,?,?)");
                    stm.setString(1, item.getId());
                    stm.setString(2, item.getName());
                    stm.setFloat(3, item.getPrice());
                    stm.setInt(4, item.getAvailable());
                    stm.setBlob(5, item.getPreview() == null ? null : new SerialBlob(item.getPreview()));

                    if (stm.executeUpdate() != 1) {
                        throw new RuntimeException("Failed to add the item");
                    }
                    res.setStatus(HttpServletResponse.SC_CREATED);
                }
            }
            
        } catch (ValidationException e) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        } catch (Throwable t) {
            t.printStackTrace();
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
    }

    private String generateId() {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT id FROM item ORDER BY id DESC LIMIT 1");
            ResultSet rst = stm.executeQuery();
            if (rst.next()){
                int nextNum = Integer.parseInt(String.valueOf(rst.getString("id")).substring(1))+1;
                if (nextNum<10){
                    return "I00"+String.valueOf(nextNum);
                } else if (nextNum<100) {
                    return "B0"+String.valueOf(nextNum);
                } else if (nextNum<1000){
                    return "I"+String.valueOf(nextNum);
                } else{
                    throw new RuntimeException("Not enough capacity");
                }
            } else {
                return "I001";
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

}
