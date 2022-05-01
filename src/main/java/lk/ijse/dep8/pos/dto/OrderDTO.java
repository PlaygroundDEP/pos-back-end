package lk.ijse.dep8.pos.dto;

import jakarta.json.bind.annotation.JsonbDateFormat;

import java.io.Serializable;
import java.sql.Date;
import java.util.List;

public class OrderDTO implements Serializable {
    private int id;
    private String nic;
    private List<String> items;
    private Float total;
    @JsonbDateFormat("yyyy-MM-dd")
    private Date date;

    public OrderDTO() {
    }

    public OrderDTO(String nic, List<String> items, Date date) {
        this.setNic(nic);
        this.setItems(items);
        this.setDate(date);
    }

    public OrderDTO(int id, String nic, List<String> items, Float total, Date date) {
        this.setId(id);
        this.setNic(nic);
        this.setItems(items);
        this.setTotal(total);
        this.setDate(date);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNic() {
        return nic;
    }

    public void setNic(String nic) {
        this.nic = nic;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }

    public Float getTotal() {
        return total;
    }

    public void setTotal(Float total) {
        this.total = total;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "OrderDTO{" +
                "id=" + id +
                ", nic='" + nic + '\'' +
                ", items=" + items +
                ", total=" + total +
                ", date=" + date +
                '}';
    }
}
