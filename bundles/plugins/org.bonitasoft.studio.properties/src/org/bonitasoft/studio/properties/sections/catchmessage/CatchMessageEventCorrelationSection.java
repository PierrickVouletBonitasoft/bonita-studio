/**
 * Copyright (C) 2012 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.bonitasoft.studio.properties.sections.catchmessage;

import java.util.ArrayList;
import java.util.List;

import org.bonitasoft.studio.common.ExpressionConstants;
import org.bonitasoft.studio.common.emf.tools.ExpressionHelper;
import org.bonitasoft.studio.common.emf.tools.ModelHelper;
import org.bonitasoft.studio.common.properties.AbstractBonitaDescriptionSection;
import org.bonitasoft.studio.expression.editor.provider.IExpressionNatureProvider;
import org.bonitasoft.studio.expression.editor.provider.IExpressionProvider;
import org.bonitasoft.studio.expression.editor.viewer.ExpressionCollectionViewer;
import org.bonitasoft.studio.model.expression.Expression;
import org.bonitasoft.studio.model.expression.ExpressionFactory;
import org.bonitasoft.studio.model.expression.ExpressionPackage;
import org.bonitasoft.studio.model.expression.ListExpression;
import org.bonitasoft.studio.model.expression.TableExpression;
import org.bonitasoft.studio.model.process.AbstractCatchMessageEvent;
import org.bonitasoft.studio.model.process.Correlation;
import org.bonitasoft.studio.model.process.CorrelationTypeActive;
import org.bonitasoft.studio.model.process.Data;
import org.bonitasoft.studio.model.process.Message;
import org.bonitasoft.studio.model.process.MessageFlow;
import org.bonitasoft.studio.model.process.ProcessPackage;
import org.bonitasoft.studio.properties.i18n.Messages;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.edit.command.AddCommand;
import org.eclipse.emf.edit.command.SetCommand;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPart;

/**
 * @author Aurelien Pupier
 * @author Aurelie Zara (add validator on correlation keys)
 *
 */
public class CatchMessageEventCorrelationSection extends
AbstractBonitaDescriptionSection {

    private ExpressionCollectionViewer ecv;


    private void doCreateControls(final Composite parent) {

        final Composite mainComposite = getWidgetFactory()
                .createComposite(parent);
        mainComposite.setLayout(GridLayoutFactory.fillDefaults().numColumns(1)
                .extendedMargins(15, 5, 15, 10).create());
        mainComposite.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
        createAutoFillButton(mainComposite);
        ecv = new ExpressionCollectionViewer(mainComposite, 5, false, 2, true,
                null, getWidgetFactory(),
                getEditingDomain(), false, false);
        ecv.setLayoutData(GridDataFactory.fillDefaults().grab(true, true)
                .hint(100, SWT.DEFAULT).create());
        ecv.setAddRowLabel(Messages.AddCorrelation);
        ecv.setRemoveRowLabel(Messages.removeCorrelation);
        final IExpressionNatureProvider provider = new CorrelationIdNatureProvider();
        ecv.addExpressionNatureProvider(provider);
        final List<ViewerFilter> filters = new ArrayList<ViewerFilter>(1);
        filters.add(new ViewerFilter() {

            @Override
            public boolean select(final Viewer viewer, final Object parentElement,
                    final Object element) {
                if (element instanceof Expression) {
                    return ExpressionConstants.CONSTANT_TYPE
                            .equals(((Expression) element).getType());
                } else if (element instanceof IExpressionProvider) {
                    return ExpressionConstants.CONSTANT_TYPE
                            .equals(((IExpressionProvider) element)
                                    .getExpressionType());
                } else {
                    return false;
                }
            }
        });
        ecv.setViewerFilters(filters);
        refresh();
    }

    private void createAutoFillButton(final Composite parent) {
        final Button autoFillButton = getWidgetFactory()
                .createButton(parent, Messages.autoFillMessageContent, SWT.FLAT);
        autoFillButton.setLayoutData(GridDataFactory.swtDefaults().indent(65, 0).hint(IDialogConstants.BUTTON_WIDTH, SWT.DEFAULT).create());
        autoFillButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                super.widgetSelected(e);
                BusyIndicator.showWhile(Display.getDefault(), new Runnable() {

                    @Override
                    public void run() {
                        createCorrelationKeyValueLinesIfNeeded();
                    }
                });

            }

            protected void createCorrelationKeyValueLinesIfNeeded() {
                final MessageFlow incomingMessag = getCatchMessageEvent().getIncomingMessag();
                if (incomingMessag != null) {
                    final Message message = ModelHelper.findEvent(
                            getCatchMessageEvent(), incomingMessag.getName());
                    if (message != null) {
                        final Correlation correlation = message.getCorrelation();
                        if (!CorrelationTypeActive.INACTIVE.equals(correlation.getCorrelationType())) {
                            final TableExpression correlationAssociationSourceMessage = correlation.getCorrelationAssociation();
                            for (final ListExpression correlationAssociationLineSourceMessage : correlationAssociationSourceMessage.getExpressions()) {
                                final Expression correlationKeySourceMessage = correlationAssociationLineSourceMessage.getExpressions().get(0);
                                final TableExpression correlationAssociationTargetMessage = getCatchMessageEvent().getCorrelation();
                                if (correlationAssociationTargetMessage != null) {
                                    boolean alreadyExists = false;
                                    final String correlationKeySourceMessageName = correlationKeySourceMessage.getName();
                                    if(correlationKeySourceMessageName != null){
                                        for (final ListExpression correlationAssociationLineTargetMessage : correlationAssociationTargetMessage.getExpressions()) {
                                            final Expression correlationKeyTargetMessage = correlationAssociationLineTargetMessage.getExpressions().get(0);
                                            if (correlationKeySourceMessageName != null
                                                    && correlationKeyTargetMessage != null
                                                    && correlationKeySourceMessageName.equals(correlationKeyTargetMessage.getName())) {
                                                alreadyExists = true;
                                                break;
                                            }
                                        }
                                        if (!alreadyExists) {
                                            createCorrelationKeyValueLineFor(correlationKeySourceMessageName);
                                        }
                                    }
                                }
                            }
                        }
                        /* refresh UI */
                        ecv.refresh();
                    }

                }
            }

            protected void createCorrelationKeyValueLineFor(
                    final String correlationKeySourceMessageName) {
                final ListExpression keyValueCorrelationExpressionToAdd = ExpressionFactory.eINSTANCE
                        .createListExpression();
                final Expression correlationKeyToAdd = ExpressionFactory.eINSTANCE
                        .createExpression();
                correlationKeyToAdd.setContent(correlationKeySourceMessageName);
                correlationKeyToAdd.setName(correlationKeySourceMessageName);
                correlationKeyToAdd.setReturnType(String.class.getName());
                correlationKeyToAdd.setType(ExpressionConstants.CONSTANT_TYPE);
                keyValueCorrelationExpressionToAdd.getExpressions().add(
                        correlationKeyToAdd);

                /* Search if there is a data with an equivalent name */
                final List<Data> accessibleData = ModelHelper
                        .getAccessibleData(getCatchMessageEvent());
                for (final Data data : accessibleData) {
                    if (correlationKeySourceMessageName.equals(data.getName())) {
                        final Expression correlationValueToAdd = ExpressionFactory.eINSTANCE
                                .createExpression();
                        correlationValueToAdd.setContent(data.getName());
                        correlationValueToAdd.setName(data.getName());
                        correlationValueToAdd .setReturnType(org.bonitasoft.studio.common.DataUtil.getTechnicalTypeFor(data));
                        correlationValueToAdd .setType(ExpressionConstants.VARIABLE_TYPE);
                        correlationValueToAdd.getReferencedElements().add(ExpressionHelper.createDependencyFromEObject(data));
                        keyValueCorrelationExpressionToAdd.getExpressions() .add(correlationValueToAdd);
                    }
                }
                final Command addCommand = AddCommand
                        .create(getEditingDomain(),
                                getCatchMessageEvent().getCorrelation(),
                                ExpressionPackage.Literals.TABLE_EXPRESSION__EXPRESSIONS,
                                keyValueCorrelationExpressionToAdd);
                getEditingDomain().getCommandStack().execute(addCommand);
            }
        });
    }

    private AbstractCatchMessageEvent getCatchMessageEvent() {
        return (AbstractCatchMessageEvent) getEObject();
    }

    @Override
    public void setInput(final IWorkbenchPart part, final ISelection selection) {
        super.setInput(part, selection);
        if (ecv != null && getEObject() != null) {
            ecv.setInput(getEObject());
            TableExpression messageCorrelation = getCatchMessageEvent()
                    .getCorrelation();
            if (messageCorrelation == null) {
                messageCorrelation = ExpressionFactory.eINSTANCE
                        .createTableExpression();
                getEditingDomain()
                .getCommandStack()
                .execute(
                        SetCommand
                        .create(getEditingDomain(),
                                getCatchMessageEvent(),
                                ProcessPackage.Literals.ABSTRACT_CATCH_MESSAGE_EVENT__CORRELATION,
                                messageCorrelation));
            }
            ecv.setSelection(messageCorrelation);

            ecv.setEditingDomain(getEditingDomain());
            ecv.refresh();
        }
    }

    @Override
    public String getSectionDescription() {
        return Messages.catchMessageEventCorrelationSectionDescription;
    }

    @Override
    protected void createContent(final Composite parent) {
        doCreateControls(parent);
    }

}
