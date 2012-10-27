package org.icescrum.web.presentation.app.project

import grails.converters.JSON
import grails.plugins.springsecurity.Secured
import org.grails.taggable.Tag
import org.icescrum.core.domain.*

class FinderController {

        def springSecurityService

        def tag(String term) {
            withProduct{ Product p ->
                if ((p.preferences.hidden && !request.inProduct) || (!p.preferences.hidden && !springSecurityService.isLoggedIn())){
                    render status:403, text:''
                    return
                }

                String findTagsByTermAndProduct = """SELECT DISTINCT tagLink.tag.name
                           FROM Story story, Feature feature, Actor actor, org.grails.taggable.TagLink tagLink
                           WHERE ((story.id = tagLink.tagRef AND story.backlog.id = :product)
                                  OR (feature.id = tagLink.tagRef AND feature.backlog.id = :product)
                                  OR (actor.id = tagLink.tagRef AND actor.backlog.id = :product))
                           AND tagLink.tag.name LIKE :term
                           ORDER BY tagLink.tag.name"""

                String findTagsByTermAndProductInTasks = """SELECT DISTINCT tagLink.tag.name
                           FROM Task task, org.grails.taggable.TagLink tagLink
                           WHERE task.id = tagLink.tagRef
                           AND tagLink.type = 'task'
                           AND task.backlog.id IN (select sprint.id from Sprint sprint, Release release WHERE sprint.parentRelease.id = release.id AND release.parentProduct.id = :product)
                           AND tagLink.tag.name LIKE :term
                           ORDER BY tagLink.tag.name"""

                def tags = Tag.executeQuery(findTagsByTermAndProduct, [term: term+'%', product: p.id])
                tags.addAll(Tag.executeQuery(findTagsByTermAndProductInTasks, [term: term+'%', product: p.id]))
                withFormat{
                    html {
                        render tags.unique() as JSON
                    }
                    json { renderRESTJSON(text:tags.unique()) }
                    xml  { renderRESTXML(text:tags.unique()) }
                 }
            }
        }

        @Secured('inProduct()')
        def list(long product, String term) {
            def data = [:]
            if (term){
                data.actors =  Actor.findAllByTagWithCriteria(term) {
                    backlog {
                        eq 'id', product
                    }
                }

                data.features = Feature.findAllByTagWithCriteria(term) {
                    backlog {
                        eq 'id', product
                    }
                }

                data.stories = Story.findAllByTagWithCriteria(term) {
                    backlog {
                        eq 'id', product
                    }
                }

                //Sort by feature AND rank
                Map storiesGrouped = data.stories?.groupBy{ it.feature }
                data.stories = []
                storiesGrouped?.each{
                    it.value?.sort{ st -> st.state }
                    data.stories.addAll(it.value)
                }

                def queryTasks ="""SELECT task
                                   FROM Task task,org.grails.taggable.TagLink tagLink
                                   WHERE   task.id = tagLink.tagRef
                                           AND tagLink.type = 'task'
                                           AND tagLink.tag.name LIKE :term
                                   ORDER BY task.name"""

                data.tasks = Task.executeQuery(queryTasks, [term: term+'%'])
            }
            withFormat{
                html {
                    render(template: 'window/postitsView', model: [data: data, user:(User)springSecurityService.currentUser])
                }
                json { renderRESTJSON(text:data) }
                xml  { renderRESTXML(text:data) }
             }
        }
}
