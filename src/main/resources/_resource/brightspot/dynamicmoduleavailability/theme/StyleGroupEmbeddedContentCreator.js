require(['jquery', 'bsp-utils'], function($, bsp_utils) {

  bsp_utils.onDomInsert(document, '.StyleEmbeddedContent-nav', {
    'insert': function (subNav) {
      $(subNav).find('.StyleEmbeddedContent-subnav-header').on('click', function () {
        // Toggle open
        $(this).toggleClass("open")
        $(this).parent().toggleClass("open")
        $(this).parent().find("ul.StyleEmbeddedContent-subnav").toggleClass("open");

        // Store open state
        const styleGroup = $(this).attr('style-group')
        if (!styleGroup) return
        const key = `BSP.ContentEdit.templateStyleGroup.clusterExpanded.${styleGroup}`

        if (window.localStorage.getItem(key) === '1') {
          window.localStorage.removeItem(key)
          return
        }

        window.localStorage.setItem(key, '1')
      });

      const navItems = $(subNav).find('ul').children()
      if (!navItems) return

      console.log(navItems)

      Array.from(navItems).forEach(item => {
        const styleGroup = $(item).find("div.StyleEmbeddedContent-subnav-header").attr('style-group');
        if (!styleGroup) return

        const key = `BSP.ContentEdit.templateStyleGroup.clusterExpanded.${styleGroup}`
        const liCurrentOpenState = window.localStorage.getItem(key)
        if (liCurrentOpenState === '1') {
          // Trigger open
          $(item).addClass("open")
          $(item).find("div.StyleEmbeddedContent-subnav-header").addClass("open");
          $(item).find("ul.StyleEmbeddedContent-subnav").addClass("open");
        }
      })
    }
  });

  bsp_utils.onDomInsert(document, '.StyleEmbeddedContent-search input', {
    'insert': (input) => {
      input.addEventListener('input', (e) => {
        const nav = input.parentElement?.parentElement
        const main = nav?.nextSibling
        const reg = new RegExp(
            e.target.value.replace(/\s/, '').split('').join('(?:.*\\W)?'),
            'i'
        )

        main?.querySelectorAll('div.StyleContainer').forEach((item) => {
          if (reg.test(item.getAttribute("name"))) {
            item.style.display = 'block'
          } else {
            item.style.display = 'none'
          }
        })

        nav?.querySelectorAll('li').forEach((li) => {
          li.style.display =
              li.classList.contains('is-selected') || e.target.value === ''
                  ? 'list-item'
                  : 'none'
          li.style.borderColor = 'var(--color-gray3)'
        })

        const firstVisible = nav?.querySelector('li[style*="display: list-item"]')
        firstVisible.style.borderColor = 'transparent'

        const warning = main?.querySelector('.StyleEmbeddedContent-warning')
        warning.style.display = main?.querySelector(
            'li[style*="display: list-item"]'
        )
            ? 'none'
            : 'block'
      })
    }
  });

});
