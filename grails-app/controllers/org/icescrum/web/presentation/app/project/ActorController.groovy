/*
 * Copyright (c) 2010 iceScrum Technologies.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 *
 */

package org.icescrum.web.presentation.app.project

import grails.converters.JSON
import grails.plugin.springcache.annotations.Cacheable
import grails.plugins.springsecurity.Secured
import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Story
import org.icescrum.core.support.ProgressSupport
import org.icescrum.core.utils.BundleUtils
import org.icescrum.plugins.attachmentable.interfaces.AttachmentException

@Secured('inProduct()')
class ActorController {
    def actorService
    def springSecurityService

    @Cacheable(cache = 'searchActors', keyGenerator = 'actorsKeyGenerator')
    def search(long product, String term) {
        def actors = Actor.findAllByProductAndTerm(product, '%' + term + '%').list()
        def result = []
        actors?.each {
            result << [label: it.name, value: it.name]
        }
        render(result as JSON)
    }

    @Secured('productOwner() and !archivedProduct()')
    def save(long product) {
        if (!params.actor) return

        def actor = new Actor()
        bindData(actor, this.params, [include:['name','description','notes','satisfactionCriteria','instances','expertnessLevel','useFrequency']], "actor")

        Product p = Product.load(product)
        try {
            actorService.save(actor, p)
            actor.tags = params.actor.tags instanceof String ? params.actor.tags.split(',') : (params.actor.tags instanceof String[] || params.actor.tags instanceof List) ? params.actor.tags : null
            this.manageAttachments(actor)
            withFormat {
                html { render status: 200, contentType: 'application/json', text: actor as JSON }
                json { renderRESTJSON(text:actor, status:201) }
                xml  { renderRESTXML(text:actor, status:201) }
            }
        } catch (RuntimeException e) {
            returnError(exception:e, object:actor)
        } catch (AttachmentException e) {
            returnError(exception:e)
        }
    }

    @Secured('productOwner() and !archivedProduct()')
    def update(long product, boolean table, String name) {
        withActor{Actor actor ->

            bindData(actor, this.params, [include:['name','description','notes','satisfactionCriteria','instances','expertnessLevel','useFrequency']], "actor")
            actor.tags = params.actor.tags instanceof String ? params.actor.tags.split(',') : (params.actor.tags instanceof String[] || params.actor.tags instanceof List) ? params.actor.tags : null
            actorService.update(actor)
            this.manageAttachments(actor)
            //if success for table view
            if (table) {
                def returnValue
                if (name == 'instances')
                    returnValue = message(code: BundleUtils.actorInstances[actor.instances])
                else if (name == 'expertnessLevel')
                    returnValue = message(code: BundleUtils.actorLevels[actor.expertnessLevel])
                else if (name == 'useFrequency')
                    returnValue = message(code: BundleUtils.actorFrequencies[actor.useFrequency])
                else if (name == 'description' || name == 'satisfactionCriteria') {
                    returnValue = actor[name]?.encodeAsHTML()?.encodeAsNL2BR()
                }
                else
                    returnValue = actor[name].encodeAsHTML()
                def version = actor.isDirty() ? actor.version + 1 : actor.version
                render(status: 200, text: [version: version, value: returnValue ?: ''] as JSON)
                return
            }
            def next = null
            if (params.continue) {
                def actors = Actor.findAllByBacklog(Product.load(product), [sort: 'useFrequency', order: 'asc']);
                def actorIndex = actors.indexOf(actor)
                if (actors.size() > actorIndex + 1)
                    next = actors[actorIndex + 1].id
            }
            withFormat {
                html { render status: 200, contentType: 'application/json', text: [actor: actor, next: next] as JSON }
                json { renderRESTJSON(text:actor) }
                xml  { renderRESTXML(text:actor) }
            }
        }
    }

    @Secured('productOwner() and !archivedProduct()')
    def delete() {
        withActors{List<Actor> actors ->
            actors.each { actor ->
                actorService.delete(actor)
            }
            def ids = []
            params.list('id').each { ids << [id: it] }
            withFormat {
                html { render status: 200, contentType: 'application/json', text: ids as JSON }
                json { render status: 204 }
                xml { render status: 204 }
            }
        }
    }

    def list(long product, String term, String windowType, String viewType) {
        def actors = term ? Actor.findAllByProductAndTerm(product, '%' + term + '%').list() : Actor.findAllByBacklog(Product.load(product), [sort: 'useFrequency', order: 'asc'])
        withFormat{
            html {
                def template = windowType == 'widget' ? 'widget/widgetView' : viewType ? 'window/' + viewType : 'window/postitsView'
                def frequenciesSelect = BundleUtils.actorFrequencies.collect {k, v -> "'$k':'${message(code: v)}'" }.join(',')
                def instancesSelect = BundleUtils.actorInstances.collect {k, v -> "'$k':'${message(code: v)}'" }.join(',')
                def levelsSelect = BundleUtils.actorLevels.collect {k, v -> "'$k':'${message(code: v)}'" }.join(',')
                render(template: template, model: [
                        actors: actors,
                        frequenciesSelect: frequenciesSelect,
                        instancesSelect: instancesSelect,
                        levelsSelect: levelsSelect])
            }
            json { renderRESTJSON(text:actors) }
            xml  { renderRESTXML(text:actors) }
         }
    }

    @Secured('productOwner() and !archivedProduct()')
    def add() {
        render(template: 'window/manage', model: [
                instancesValues: BundleUtils.actorInstances.values().collect {v -> message(code: v)},
                instancesKeys: BundleUtils.actorInstances.keySet().asList(),
                levelsValues: BundleUtils.actorLevels.values().collect {v -> message(code: v)},
                levelsKeys: BundleUtils.actorLevels.keySet().asList(),
                frequenciesValues: BundleUtils.actorFrequencies.values().collect {v -> message(code: v)},
                frequenciesKeys: BundleUtils.actorFrequencies.keySet().asList(),
        ])
    }

    @Secured('productOwner() and !archivedProduct()')
    def edit(long product) {
        withActor{ Actor actor ->
            def actors = Actor.findAllByBacklog(Product.load(product), [sort: 'useFrequency', order: 'asc']);
            def next = null
            def actorIndex = actors.indexOf(actor)
            if (actors.size() > actorIndex + 1)
                next = actors[actorIndex + 1].id

            render(template: 'window/manage', model: [
                    actor: actor,
                    next: next ?: '',
                    instancesValues: BundleUtils.actorInstances.values().collect {v -> message(code: v)},
                    instancesKeys: BundleUtils.actorInstances.keySet().asList(),
                    levelsValues: BundleUtils.actorLevels.values().collect {v -> message(code: v)},
                    levelsKeys: BundleUtils.actorLevels.keySet().asList(),
                    frequenciesValues: BundleUtils.actorFrequencies.values().collect {v -> message(code: v)},
                    frequenciesKeys: BundleUtils.actorFrequencies.keySet().asList(),
            ])
        }
    }

    private manageAttachments(def actor) {
        def user = springSecurityService.currentUser
        def needPush = false
        if (params.actor.attachments && actor.id && !params.actor.list('attachments') && actor.attachments*.id.size() > 0) {
            actor.removeAllAttachments()
            needPush = true
        } else if (actor.attachments*.id.size() > 0) {
            actor.attachments*.id.each {
                if (!params.actor.list('attachments').contains(it.toString()))
                    actor.removeAttachment(it)
                    needPush = true
            }
        }
        def uploadedFiles = []
        params.list('attachments')?.each { attachment ->
            "${attachment}".split(":").with {
                if (session.uploadedFiles[it[0]])
                    uploadedFiles << [file: new File((String) session.uploadedFiles[it[0]]), name: it[1]]
            }
        }
        if (uploadedFiles){
            actor.addAttachments(user, uploadedFiles)
            needPush = true
        }
        session.uploadedFiles = null
        if (needPush){
            actor.lastUpdated = new Date()
            broadcast(function: 'update', message: actor)
        }
    }

    def show() {
        redirect(action:'index', controller: controllerName, params:params)
    }

    @Cacheable(cache = 'actorCache', keyGenerator='actorKeyGenerator')
    def index() {
        if (request?.format == 'html'){
            render(status:404)
            return
        }

        withActor{ Actor actor ->
            withFormat {
                json { renderRESTJSON(text:actor) }
                xml  { renderRESTXML(text:actor) }
            }
        }
    }

    def print(long product, String format, boolean get, boolean status) {
        def p = Product.load(product)
        def data = []
        def actors = Actor.findAllByBacklog(p, [sort: 'useFrequency', order: 'asc']);
        if (!actors) {
            returnError(text:message(code: 'is.report.error.no.data'))
            return
        } else if (get) {
            actors.each {
                data << [
                        name: it.name,
                        description: it.description,
                        notes: it.notes?.replaceAll(/<.*?>/, ''),
                        expertnessLevel: message(code: BundleUtils.actorLevels[it.expertnessLevel]),
                        satisfactionCriteria: it.satisfactionCriteria,
                        useFrequency: message(code: BundleUtils.actorFrequencies[it.useFrequency]),
                        instances: BundleUtils.actorInstances[it.instances],
                        associatedStories: Story.findAllByTextAsIlike(it.name).size() ?: 0
                ]
            }
            outputJasperReport('actors', format, [[product: p.name, actors: data ?: null]], p.name)
        } else if (status) {
            render(status: 200, contentType: 'application/json', text: session.progress as JSON)
        } else {
            session.progress = new ProgressSupport()
            def dialog = g.render(template: '/scrumOS/report')
            render(status: 200, contentType: 'application/json', text: [dialog:dialog] as JSON)
        }
    }

    def download(long id) {
        forward(action: 'download', controller: 'attachmentable', id: id)
        return
    }
}
