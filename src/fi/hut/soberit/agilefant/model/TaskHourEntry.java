package fi.hut.soberit.agilefant.model;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.BatchSize;

import flexjson.JSON;

@Entity
@BatchSize(size = 20)
public class TaskHourEntry extends HourEntry {

    private Task task;

    public void setTask(Task task) {
        this.task = task;
    }

    @ManyToOne
    @JSON(include = false)
    @XmlTransient
    public Task getTask() {
        return task;
    }

}
