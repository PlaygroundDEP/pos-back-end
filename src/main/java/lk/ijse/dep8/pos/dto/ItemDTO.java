package lk.ijse.dep8.pos.dto;

import jakarta.json.bind.annotation.JsonbTransient;

import java.io.Serializable;
import java.util.Arrays;

public class ItemDTO implements Serializable {
    private String id;
    private String name;
    private float price;
    private int available;
    @JsonbTransient
    private byte[] preview;

    public ItemDTO() {
    }

    public ItemDTO(String id, String name, float price, int available) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.available = available;
    }

    public ItemDTO(String id, String name, float price, int available, byte[] preview) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.available = available;
        this.preview = preview;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getPrice() {
        return price;
    }

    public void setPrice(float price) {
        this.price = price;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public byte[] getPreview() {
        return preview;
    }

    public void setPreview(byte[] preview) {
        this.preview = preview;
    }

    @Override
    public String toString() {
        return "ItemDTO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", available=" + available +
                ", preview=" + Arrays.toString(preview) +
                '}';
    }
}
