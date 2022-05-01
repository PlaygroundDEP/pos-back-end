package lk.ijse.dep8.pos.dto;

import java.io.Serializable;

public class CustomerDTO implements Serializable {
    private String nic;
    private String name;
    private String address;
    private String contact;

    public CustomerDTO() {
    }

    public CustomerDTO(String name, String address, String contact) {
        this.name = name;
        this.address = address;
        this.contact = contact;
    }

    public CustomerDTO(String nic, String name, String address, String contact) {
        this.nic = nic;
        this.name = name;
        this.address = address;
        this.contact = contact;
    }

    public String getNic() {
        return nic;
    }

    public void setNic(String nic) {
        this.nic = nic;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    @Override
    public String toString() {
        return "CustomerDTO{" +
                "nic='" + nic + '\'' +
                ", name='" + name + '\'' +
                ", address='" + address + '\'' +
                ", contact='" + contact + '\'' +
                '}';
    }
}
