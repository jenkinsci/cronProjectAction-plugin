package com.shawinc.jenkins.plugin;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.TransientProjectActionFactory;
import java.util.ArrayList;
import java.util.Collection;

/**
 *
 * @author gcampb2
 */
@Extension
public class CronProjectActionFactory  extends TransientProjectActionFactory {

    @Override
    public Collection<? extends Action> createFor(@SuppressWarnings("unchecked") AbstractProject target) {
        final ArrayList<Action> actions = new ArrayList<Action>();
		actions.add(new CronProjectAction(target));

        return actions;
    }

}
