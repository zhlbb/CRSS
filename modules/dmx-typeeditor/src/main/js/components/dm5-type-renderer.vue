<template>
  <div class="dm5-type-renderer">
    <!-- Type Value -->
    <dm5-value-renderer :object="object" :level="0" :context="context"></dm5-value-renderer>
    <!-- Type URI -->
    <div class="field">
      <div class="field-label">Type URI</div>
      <div v-if="infoMode">{{object.uri}}</div>
      <el-input v-else v-model="object.uri"></el-input>
    </div>
    <!-- Data Type -->
    <div class="field">
      <div class="field-label">Data Type</div>
      <div v-if="infoMode">{{dataType.value}}</div>
      <dm5-data-type-select v-else :type="type"></dm5-data-type-select>
    </div>
    <!-- Comp Defs -->
    <dm5-comp-def-list :comp-defs="compDefs" :mode="mode" @comp-def-click="click"></dm5-comp-def-list>
  </div>
</template>

<script>
import dm5 from 'dm5'

export default {

  created () {
    // console.log('dm5-type-renderer created', this.type)
  },

  mixins: [
    require('./mixins/info-mode').default,
    require('./mixins/context').default
  ],

  props: {
    object: {   // the type to render
      type: dm5.Type,
      required: true
    }
  },

  computed: {

    type () {
      return this.object
    },

    mode () {
      return this.context.mode
    },

    dataType () {
      return this.type.getDataType()
    },

    compDefs () {
      return this.type.compDefs
    }
  },

  methods: {
    click (compDef) {
      const childType = compDef.getChildType()
      childType.assoc = compDef    // type cache side effect ### FIXME
      this.$store.dispatch('revealRelatedTopic', {relTopic: childType})
    }
  },

  components: {
    'dm5-data-type-select': require('./dm5-data-type-select').default,
    'dm5-comp-def-list':    require('./dm5-comp-def-list').default
  }
}
</script>

<style>
.dm5-type-renderer > .field,
.dm5-type-renderer .dm5-comp-def-list {
  margin-top: var(--field-spacing);
}
</style>
