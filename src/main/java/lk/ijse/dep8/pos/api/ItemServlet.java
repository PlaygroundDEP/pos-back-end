package lk.ijse.dep8.pos.api;

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

@MultipartConfig(location = "/tmp", maxFileSize = 15 * 1024 * 1024)
@WebServlet(name = "ItemServlet", value = {"/items/*"})
public class ItemServlet extends HttpServlet {



    @Resource(name = "java:comp/env/jdbc/pool4pos")
    private volatile DataSource pool;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doSaveOrUpdate(req, resp);
    }

    private void doSaveOrUpdate(HttpServletRequest req, HttpServletResponse res) throws IOException {
        System.out.println(req.getParameter("available"));

        if (req.getContentType()==null || req.getContentType().toLowerCase().startsWith("multipart/form-date")) {
            res.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        String method = req.getMethod();
        String pathInfo = req.getPathInfo();

        if (method.equals("POST") && (pathInfo!=null && !pathInfo.equals("/"))) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        
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

            try(Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("INSERT INTO item (id, name, price, available, preview) VALUES (?,?,?,?,?)");
                stm.setString(1,item.getId());
                stm.setString(2, item.getName());
                stm.setFloat(3, item.getPrice());
                stm.setInt(4, item.getAvailable());
                stm.setBlob(5, item.getPreview() == null ? null : new SerialBlob(item.getPreview()));

                if (stm.executeUpdate() != 1) {
                    throw new RuntimeException("Failed to add a book");
                }
                res.setStatus(HttpServletResponse.SC_CREATED);
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
