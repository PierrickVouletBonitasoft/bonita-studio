/**
 * Copyright (C) 2014 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.bonitasoft.studio.properties.operation;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.bonitasoft.studio.common.NamingUtils;
import org.bonitasoft.studio.common.diagram.dialog.ProcessesNameVersion;
import org.bonitasoft.studio.common.emf.tools.ModelHelper;
import org.bonitasoft.studio.common.repository.Repository;
import org.bonitasoft.studio.common.repository.RepositoryManager;
import org.bonitasoft.studio.diagram.custom.operation.DuplicateDiagramOperation;
import org.bonitasoft.studio.diagram.custom.repository.DiagramFileStore;
import org.bonitasoft.studio.diagram.custom.repository.DiagramRepositoryStore;
import org.bonitasoft.studio.diagram.custom.repository.ProcessConfigurationFileStore;
import org.bonitasoft.studio.diagram.custom.repository.ProcessConfigurationRepositoryStore;
import org.bonitasoft.studio.model.process.MainProcess;
import org.bonitasoft.studio.model.process.Pool;
import org.bonitasoft.studio.model.process.ProcessPackage;
import org.bonitasoft.studio.properties.i18n.Messages;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.gmf.runtime.diagram.ui.parts.DiagramEditor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.ui.IWorkbenchPart;

/**
 * @author Romain Bioteau
 */
public class RenameDiagramOperation implements IRunnableWithProgress {

    private MainProcess diagram;
    private String diagramVersion;
    private String diagramName;
    private List<ProcessesNameVersion> pools = new ArrayList<ProcessesNameVersion>();
    private DiagramEditor editor;

    /*
     * (non-Javadoc)
     * @see org.eclipse.jface.operation.IRunnableWithProgress#run(org.eclipse.core.runtime.IProgressMonitor)
     */
    @Override
    public void run(final IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
        Assert.isNotNull(diagram);
        Assert.isNotNull(diagramVersion);
        Assert.isNotNull(diagramName);
        Assert.isNotNull(editor);
        monitor.beginTask(Messages.renamingDiagram, IProgressMonitor.UNKNOWN);

        final String oldName = diagram.getName();
        final String oldVersion = diagram.getVersion();

        final String partName = editor.getPartName();
        final DiagramRepositoryStore diagramStore = RepositoryManager.getInstance()
                .getRepositoryStore(DiagramRepositoryStore.class);

        final DuplicateDiagramOperation operation = new DuplicateDiagramOperation();
        operation.setDiagramToDuplicate(diagram);
        operation.setNewDiagramName(diagramName);
        operation.setNewDiagramVersion(diagramVersion);
        operation.setPoolsRenamed(pools);
        operation.run(Repository.NULL_PROGRESS_MONITOR);

        if (!(oldName.equals(diagramName) && oldVersion.equals(diagramVersion))) {
            final DiagramFileStore diagramFileStore = diagramStore.getDiagram(oldName, oldVersion);
            diagramFileStore.getOpenedEditor().doSave(Repository.NULL_PROGRESS_MONITOR);
            cleanOldFileStores(diagramFileStore);
            reopenEditors(partName, diagramStore);
        }
    }

    protected void reopenEditors(final String partName, final DiagramRepositoryStore diagramStore) {
        final DiagramFileStore fStore = diagramStore.getChild(NamingUtils.toDiagramFilename(diagramName, diagramVersion));
        fStore.save(null);
        IWorkbenchPart partToActivate = fStore.open();
        partToActivate.getSite().getPage().activate(partToActivate);
    }

    protected void cleanOldFileStores(final DiagramFileStore diagramFileStore) {
        final List<Pool> allPools = ModelHelper.getAllItemsOfType(diagram, ProcessPackage.Literals.POOL);
        final ProcessConfigurationRepositoryStore confStore = RepositoryManager.getInstance()
                .getRepositoryStore(ProcessConfigurationRepositoryStore.class);
        for (final Pool p : allPools) {
            ProcessConfigurationFileStore fileStore = confStore
                    .getChild(ModelHelper.getEObjectID(p) + "." + ProcessConfigurationRepositoryStore.CONF_EXT);
            if (fileStore != null) {
                fileStore.delete();
            }
        }
        diagramFileStore.close();
        diagramFileStore.delete();
    }

    public void setDiagramToDuplicate(final MainProcess diagram) {
        this.diagram = diagram;
    }

    public void setNewDiagramName(final String diagramName) {
        this.diagramName = diagramName;
    }

    public void setPoolsRenamed(final List<ProcessesNameVersion> pools) {
        this.pools = pools;
    }

    public void setNewDiagramVersion(final String diagramVersion) {
        this.diagramVersion = diagramVersion;
    }

    public void setEditor(final DiagramEditor editor) {
        this.editor = editor;
    }

}
