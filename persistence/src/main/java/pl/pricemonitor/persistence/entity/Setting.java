package pl.pricemonitor.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Key-value system setting changed by the administrator. */
@Entity
@Table(name = "ustawienie")
public class Setting {

    @Id
    @Column(name = "klucz", nullable = false)
    private String key;

    @Column(name = "wartosc", nullable = false)
    private String value;

    protected Setting() {
    }

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
